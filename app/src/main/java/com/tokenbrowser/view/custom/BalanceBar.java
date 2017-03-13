package com.tokenbrowser.view.custom;


import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tokenbrowser.model.network.Balance;
import com.tokenbrowser.token.R;
import com.tokenbrowser.util.EthUtil;
import com.tokenbrowser.util.SoundManager;
import com.tokenbrowser.view.BaseApplication;

import java.math.BigInteger;

import rx.android.schedulers.AndroidSchedulers;

public class BalanceBar extends LinearLayout {

    private BigInteger previousWeiBalance;

    public void setOnBalanceClicked(final OnClickListener listener) {
        findViewById(R.id.balanceWrapper).setOnClickListener(listener);
    }

    public void setOnPayClicked(final OnClickListener listener) {
        findViewById(R.id.pay_button).setOnClickListener(listener);
        findViewById(R.id.pay_button).setVisibility(VISIBLE);
    }

    public void setOnRequestClicked(final OnClickListener listener) {
        findViewById(R.id.request_button).setOnClickListener(listener);
        findViewById(R.id.request_button).setVisibility(VISIBLE);
    }

    public BalanceBar(final Context context) {
        super(context);
        init();
    }

    public BalanceBar(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.view__balance_bar, this);
        setEmptyValues();
        setBalance();
    }

    private void setEmptyValues() {
        showBalanceInUi(BigInteger.ZERO);
    }

    private void setBalance() {
        BaseApplication
                .get()
                .getTokenManager()
                .getBalanceManager()
                .getBalanceObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::handleNewBalance);
    }

    private void handleNewBalance(final Balance balance) {
        setEthBalanceFromBigInteger(balance.getUnconfirmedBalance());
        setLocalBalance(balance.getFormattedLocalBalance());
    }

    private void setEthBalanceFromBigInteger(final BigInteger weiBalance) {
        showBalanceInUi(weiBalance);
        tryPlaySound(weiBalance);
    }

    private void tryPlaySound(final BigInteger weiBalance) {
        if (this.previousWeiBalance == null) {
            this.previousWeiBalance = weiBalance;
            return;
        }

        if (this.previousWeiBalance.compareTo(weiBalance) == 0) {
            return;
        }

        this.previousWeiBalance = weiBalance;
        SoundManager.getInstance().playSound(SoundManager.BALANCE_CHANGE);
    }

    private void showBalanceInUi(final BigInteger weiBalance) {
        final String stringBalance = EthUtil.weiAmountToUserVisibleString(weiBalance);
        ((TextView) findViewById(R.id.eth_balance)).setText(stringBalance);
    }

    private void setLocalBalance(final String localBalance) {
        ((TextView) findViewById(R.id.local_currency_balance)).setText(localBalance);
    }

}
