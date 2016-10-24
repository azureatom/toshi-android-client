package com.bakkenbaeck.token.presenter;

import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.bakkenbaeck.token.R;
import com.bakkenbaeck.token.model.ActivityResultHolder;
import com.bakkenbaeck.token.model.LocalBalance;
import com.bakkenbaeck.token.model.User;
import com.bakkenbaeck.token.network.rest.TokenService;
import com.bakkenbaeck.token.network.rest.model.SignatureRequest;
import com.bakkenbaeck.token.network.rest.model.SignedWithdrawalRequest;
import com.bakkenbaeck.token.network.rest.model.TransactionSent;
import com.bakkenbaeck.token.network.rest.model.WithdrawalRequest;
import com.bakkenbaeck.token.network.ws.model.TransactionConfirmation;
import com.bakkenbaeck.token.util.EthUtil;
import com.bakkenbaeck.token.util.LogUtil;
import com.bakkenbaeck.token.util.OnNextObserver;
import com.bakkenbaeck.token.util.OnNextSubscriber;
import com.bakkenbaeck.token.util.OnSingleClickListener;
import com.bakkenbaeck.token.util.RetryWithBackoff;
import com.bakkenbaeck.token.util.SnackbarUtil;
import com.bakkenbaeck.token.view.BaseApplication;
import com.bakkenbaeck.token.view.activity.BarcodeScannerActivity;
import com.bakkenbaeck.token.view.activity.WithdrawActivity;
import com.bakkenbaeck.token.view.adapter.PreviousWalletAddress;
import com.bakkenbaeck.token.view.dialog.ProgressDialog;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Locale;

import retrofit2.Response;
import rx.Subscriber;

import static android.app.Activity.RESULT_OK;

public class WithdrawPresenter implements Presenter<WithdrawActivity> {
    private static final String TAG = "WithdrawPresenter";
    static final String INTENT_WALLET_ADDRESS = "wallet_address";
    static final String INTENT_WITHDRAW_AMOUNT = "withdraw_amount";

    private WithdrawActivity activity;
    private User currentUser;
    private boolean firstTimeAttaching = true;
    private BigDecimal currentBalance = BigDecimal.ZERO;
    private BigDecimal currentUnconfirmedBalance = BigDecimal.ZERO;
    private ProgressDialog progressDialog;
    private final BigDecimal minWithdrawLimit = new BigDecimal("0.0000000001");

    private final PreviousWalletAddress previousWalletAddress = new PreviousWalletAddress();

    @Override
    public void onViewAttached(final WithdrawActivity activity) {
        this.activity = activity;
        initButtons();
        initToolbar();
        initPreviousAddress();

        if (firstTimeAttaching) {
            firstTimeAttaching = false;
            registerObservables();
            initProgressDialog2();
        }
    }

    private void initProgressDialog2(){
        progressDialog = ProgressDialog.newInstance();
    }

    private void initButtons() {
        this.activity.getBinding().barcodeButton.setOnClickListener(new OnSingleClickListener() {
            @Override
            public void onSingleClick(final View v) {
                showBarcodeActivity();
            }
        });

        this.activity.getBinding().maxButton.setOnClickListener(new OnSingleClickListener() {
            @Override
            public void onSingleClick(View v) {
                BaseApplication.get().getLocalBalanceManager().getObservable().subscribe(new OnNextSubscriber<LocalBalance>() {
                    @Override
                    public void onNext(LocalBalance localBalance) {
                        this.unsubscribe();
                        tryPopulateAmountField(localBalance);
                    }
                });
            }
        });

        this.activity.getBinding().sendButton.setOnClickListener(new OnSingleClickListener() {
            @Override
            public void onSingleClick(final View view) {
                handleSendClicked();
            }
        });

        this.activity.getBinding().walletAddress.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence charSequence, final int i, final int i1, final int i2) {}

            @Override
            public void onTextChanged(final CharSequence charSequence, final int i, final int i1, final int i2) {
                updateSendButtonEnabledState();
            }

            @Override
            public void afterTextChanged(final Editable editable) {}
        });

        this.activity.getBinding().amount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButtonEnabledState();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        //refreshButtonStates();
    }

    private void updateSendButtonEnabledState() {
        final Editable walletAddress = this.activity.getBinding().walletAddress.getText();
        String amount = this.activity.getBinding().amount.getText().toString();
        final boolean shouldEnableButton = walletAddress.length() > 0 && userHasEnoughReputationScore() && amount.length() > 0;
        enableSendButton(shouldEnableButton);
        activity.getBinding().sendButton.setEnabled(shouldEnableButton);
    }

    private void enableSendButton(boolean enabled){
        if(enabled){
            activity.getBinding().sendButton.setTextColor(Color.parseColor("#FFFFFF"));
            activity.getBinding().sendButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.btn_with_radius));
        }else{
            activity.getBinding().sendButton.setTextColor(Color.parseColor("#33565A64"));
            activity.getBinding().sendButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.disabled_background));
        }
    }

    private void initToolbar() {
        final String title = this.activity.getResources().getString(R.string.withdraw__title);
        final Toolbar toolbar = this.activity.getBinding().toolbar;
        this.activity.setSupportActionBar(toolbar);
        this.activity.getSupportActionBar().setTitle(title);
        this.activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void initPreviousAddress() {
        final EditText walletAddress = this.activity.getBinding().walletAddress;
        walletAddress.setText(this.previousWalletAddress.getAddress());
        walletAddress.setSelection(walletAddress.getText().length());
    }

    private final OnNextObserver<LocalBalance> newBalanceSubscriber = new OnNextObserver<LocalBalance>() {
        @Override
        public void onNext(final LocalBalance newBalance) {
            if (activity != null && newBalance != null) {
                currentBalance = newBalance.getConfirmedBalanceAsEthMinusTransferFee();
                currentUnconfirmedBalance = newBalance.getUnconfirmedBalanceAsEthMinusTransferFee();
                activity.getBinding().balanceBar.setBalance(newBalance.unconfirmedBalanceString());
            }
        }
    };

    private final OnNextObserver<Integer> newReputationSubscriber = new OnNextObserver<Integer>() {
        @Override
        public void onNext(Integer reputationScore) {
            if(activity != null){
                activity.getBinding().balanceBar.setReputation(reputationScore);
            }
        }
    };

    private void tryPopulateAmountField(LocalBalance localBalance) {
        final BigDecimal unconfirmedBalance = localBalance.getUnconfirmedBalanceAsEth();
        final BigDecimal confirmedBalance = localBalance.getConfirmedBalanceAsEth();
        final String amount;

        if(localBalance.getConfirmedBalance().equals(BigInteger.ZERO)){
            SnackbarUtil.make(activity.getBinding().root, "Your balance is 0").show();
            return;
        }

        try {

            if(unconfirmedBalance.compareTo(confirmedBalance) == -1){
                amount = localBalance.unconfirmedBalanceStringMinusTransferFee();
            }else if(confirmedBalance.compareTo(unconfirmedBalance) == -1){
                amount = localBalance.confirmedBalanceStringMinusTransferFee();
            }else{
                amount = localBalance.confirmedBalanceStringMinusTransferFee().replace(",", ".");
            }

            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if(activity != null) {
                        activity.getBinding().amount.setText(amount);
                        activity.getBinding().amount.setSelection(activity.getBinding().amount.getText().length());
                    }
                }
            });
        }catch (IndexOutOfBoundsException e){
            LogUtil.d(getClass(), e.getMessage());
        }
    }

    @Override
    public void onViewDetached() {
        String walletAddress = this.activity.getBinding().walletAddress.getText().toString();
        previousWalletAddress.setAddress(walletAddress);

        this.activity = null;
    }

    @Override
    public void onViewDestroyed() {
        this.activity = null;
    }

    public void handleActivityResult(final ActivityResultHolder activityResultHolder) {
        if (activityResultHolder.getResultCode() != RESULT_OK) {
            return;
        }

        tryParseBarcodeResult(activityResultHolder);
    }

    private void showBarcodeActivity() {
        new IntentIntegrator(this.activity)
                .setCaptureActivity(BarcodeScannerActivity.class)
                .setOrientationLocked(true)
                .setPrompt("")
                .setBeepEnabled(true)
                .initiateScan();
    }

    private void tryParseBarcodeResult(final ActivityResultHolder activityResultHolder) {
        final IntentResult result = IntentIntegrator.parseActivityResult(
                activityResultHolder.getRequestCode(),
                activityResultHolder.getResultCode(),
                activityResultHolder.getIntent());
        if(result == null || result.getContents() == null || result.getContents().length() <= 0) {
            return;
        }

        this.activity.getBinding().walletAddress.setText(result.getContents());
    }

    private void handleSendClicked() {
        if (!validate()) {
            return;
        }

        showAddressError(false, "");
        showBalanceError(false, "");

        try {
            final DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance(Locale.ENGLISH);
            nf.setParseBigDecimal(true);
            final String inputtedText = this.activity.getBinding().amount.getText().toString();
            final String checkSepators = inputtedText.replace("," , ".");

            final BigDecimal amountInEth = (BigDecimal) nf.parse(checkSepators);

            final BigInteger amountInWei = EthUtil.ethToWei(amountInEth);
            final String toAddress = this.activity.getBinding().walletAddress.getText().toString();
            final WithdrawalRequest withdrawalRequest = new WithdrawalRequest(amountInWei, toAddress);

            TokenService.getApi()
                    .postWithdrawalRequest(this.currentUser.getAuthToken(), withdrawalRequest)
                    .subscribe(generateSigningSubscriber());
            progressDialog.show(this.activity.getSupportFragmentManager(), "progressDialog");
            this.previousWalletAddress.setAddress(toAddress);
        } catch (final ParseException ex) {
            LogUtil.e(getClass(), ex.toString());
        }
    }

    private Subscriber<Response<SignatureRequest>> generateSigningSubscriber() {
        return new Subscriber<Response<SignatureRequest>>() {
            @Override
            public void onCompleted() {}

            @Override
            public void onError(final Throwable ex) {
                Log.d(TAG, "onError: 1 " + ex);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                    }
                });
                LogUtil.e(getClass(), "postWithdrawalRequest: " + ex);
            }

            @Override
            public void onNext(final Response<SignatureRequest> signatureRequest) {
                Log.d(TAG, "onNext: 1");
                if(signatureRequest.code() == 400 || signatureRequest.code() == 500){
                    //Getting null when trying to parse when error
                    showAddressError(true, "Enter a valid address");
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                        }
                    });
                }else if(signatureRequest.code() == 200) {

                    final String unsignedTransaction = signatureRequest.body().getTransaction();
                    Log.d(TAG, "onNext: -> unsignedTransaction " + unsignedTransaction);
                    final String signature = BaseApplication.get().getUserManager().signTransaction(unsignedTransaction);

                    Log.d(TAG, "onNext: -> " + signature);

                    final SignedWithdrawalRequest request = new SignedWithdrawalRequest(unsignedTransaction, signature);

                    TokenService.getApi()
                            .postSignedWithdrawal(currentUser.getAuthToken(), request)
                            .retryWhen(new RetryWithBackoff(5))
                            .subscribe(generateSignedWithdrawalSubscriber());
                }
            }

            private Subscriber<Response<TransactionSent>> generateSignedWithdrawalSubscriber() {
                return new Subscriber<Response<TransactionSent>>() {
                    @Override
                    public void onCompleted() {}

                    @Override
                    public void onError(final Throwable ex) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                progressDialog.dismiss();
                            }
                        });
                        LogUtil.e(getClass(), "postSignedWithdrawal: " + ex);
                    }

                    @Override
                    public void onNext(final Response<TransactionSent> transactionSent) {
                        Log.d(TAG, "onNext: 2");
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                if(progressDialog != null) {
                                    progressDialog.dismiss();
                                }
                            }
                        });

                        if(transactionSent.code() == 400){
                            showBalanceError(true, "Not enough funds to transfer the requested amount");
                            return;
                        }

                        BigInteger t1 = transactionSent.body().getConfirmedBalance();
                        BigInteger t2 = transactionSent.body().getUnconfirmedBalance();
                        TransactionConfirmation t = new TransactionConfirmation(t1.toString(), t2.toString());
                        BaseApplication.get().getSocketObservables().emitTransactionConfirmation(t);
                        BaseApplication.get().getSocketObservables().emitTransactionSent(transactionSent.body());
                        String parsedInput = activity.getBinding().amount.getText().toString().replace(",", ".");

                        final Intent intent = new Intent();
                        intent.putExtra(INTENT_WALLET_ADDRESS, activity.getBinding().walletAddress.getText().toString());
                        intent.putExtra(INTENT_WITHDRAW_AMOUNT, new BigDecimal(parsedInput));
                        activity.setResult(RESULT_OK, intent);
                        activity.finish();
                        activity.overridePendingTransition(R.anim.enter_fade_in, R.anim.exit_fade_out);
                    }
                };
            }
        };
    }

    private void showAddressError(final boolean show, final String errorMessage){
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                TextView addressError = activity.getBinding().ethErrorMessage;
                View addressLine = activity.getBinding().addressLine;
                if(show){
                    addressError.setText(errorMessage);
                    addressError.setVisibility(View.VISIBLE);
                    addressLine.setBackgroundColor(ContextCompat.getColor(activity, R.color.errorState));
                }else{
                    addressError.setVisibility(View.INVISIBLE);
                    addressLine.setBackgroundColor(ContextCompat.getColor(activity, R.color.divider));
                }
            }
        });
    }

    private void showBalanceError(final boolean show, final String errorMassage){
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                TextView balanceError = activity.getBinding().balanceErrorMessage;
                View balanceLine = activity.getBinding().balanceLine;
                if(show){
                    balanceError.setText(errorMassage);
                    balanceError.setVisibility(View.VISIBLE);
                    balanceLine.setBackgroundColor(ContextCompat.getColor(activity, R.color.errorState));
                    balanceError.setTextColor(ContextCompat.getColor(activity, R.color.errorState));
                }else{
                    balanceError.setTextColor(ContextCompat.getColor(activity, R.color.hintText));
                    balanceLine.setBackgroundColor(ContextCompat.getColor(activity, R.color.divider));
                }
            }
        });
    }

    private boolean validate() {
        BigDecimal amountRequested = BigDecimal.ZERO;

        try {
            final DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance(Locale.ENGLISH);
            nf.setParseBigDecimal(true);
            final String inputtedText = this.activity.getBinding().amount.getText().toString();
            String checkSepators = inputtedText.replace("," , ".");
            amountRequested = (BigDecimal) nf.parse(checkSepators);
            Log.d(TAG, "validate: amount requested " + amountRequested + " current balance " + currentBalance);

            final String toAddress = this.activity.getBinding().walletAddress.getText().toString();
            this.activity.getBinding().walletAddress.setText(toAddress.replaceFirst("ethereum:", ""));

            if (amountRequested.compareTo(minWithdrawLimit) > 0 && amountRequested.compareTo(this.currentBalance) <= 0) {
                return true;
            }
        } catch (final NumberFormatException | ParseException ex) {
            LogUtil.e(getClass(), ex.toString());
        }

        String errorMessage;

        Log.d(TAG, "validate: unconfirmed " + currentUnconfirmedBalance + " confirmed " + currentBalance);

        if(this.currentBalance.compareTo(BigDecimal.ZERO) == 0){
            errorMessage = this.activity.getResources().getString(R.string.withdraw__amount_error_zero);
        }else if(amountRequested != null && amountRequested.compareTo(currentBalance) == 1){
            errorMessage = this.activity.getResources().getString(R.string.withdraw__amount_error_bigger);
        }else{
            errorMessage = this.activity.getResources().getString(R.string.withdraw__amount_error);
        }

        showBalanceError(true, errorMessage);

        this.activity.getBinding().amount.requestFocus();
        return false;
    }

    private boolean userHasEnoughReputationScore() {
        // Todo: Reputation required for withdrawal should be dictated by the server
        if (currentUser == null || currentUser.getLevel() == 0) {
            return false;
        }
        return true;
    }

    private void registerObservables() {
        BaseApplication.get().getLocalBalanceManager().getObservable().subscribe(this.newBalanceSubscriber);
        BaseApplication.get().getUserManager().getObservable().subscribe(this.userSubscriber);
        BaseApplication.get().getLocalBalanceManager().getLevelObservable().subscribe(this.newReputationSubscriber);
    }

    private final OnNextObserver<User> userSubscriber = new OnNextObserver<User>() {
        @Override
        public void onNext(final User user) {
            currentUser = user;
            refreshButtonStates();
            this.onCompleted();
        }
    };

    private void refreshButtonStates() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (activity == null) {
                    return;
                }
                updateSendButtonEnabledState();
            }
        });
    }
}