package com.bakkenbaeck.token.view.adapter;


import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bakkenbaeck.token.R;
import com.bakkenbaeck.token.manager.store.ContactStore;
import com.bakkenbaeck.token.model.local.Contact;
import com.bakkenbaeck.token.model.local.User;
import com.bakkenbaeck.token.util.SingleSuccessSubscriber;
import com.bakkenbaeck.token.view.BaseApplication;
import com.bakkenbaeck.token.view.adapter.listeners.OnItemClickListener;
import com.bakkenbaeck.token.view.adapter.viewholder.ClickableViewHolder;
import com.bakkenbaeck.token.view.adapter.viewholder.ContactViewHolder;

import java.util.ArrayList;
import java.util.List;

import rx.android.schedulers.AndroidSchedulers;

public class ContactsAdapter extends RecyclerView.Adapter<ContactViewHolder> implements ClickableViewHolder.OnClickListener {

    private List<User> users;
    private OnItemClickListener<User> onItemClickListener;

    public ContactsAdapter() {
        this.users = new ArrayList<>(0);
    }

    public ContactsAdapter loadAllStoredContacts() {
        new ContactStore()
                .loadAll()
                .subscribe(new SingleSuccessSubscriber<List<Contact>>() {
                    @Override
                    public void onSuccess(final List<Contact> contacts) {
                        ContactsAdapter.this.users = new ArrayList<>(contacts.size());
                        for (final Contact contact : contacts) {
                            ContactsAdapter.this.users.add(contact.getUser());
                        }
                        notifyDataSetChanged();
                    }
                });
        return this;
    }

    public void filter(final String searchString) {
        BaseApplication
                .get()
                .getTokenManager()
                .getUserManager()
                .searchOfflineUsers(searchString)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::setUsers);
    }


    public ContactsAdapter setUsers(final List<User> users) {
        this.users = users;
        notifyDataSetChanged();
        return this;
    }

    @Override
    public ContactViewHolder onCreateViewHolder(final ViewGroup parent, int viewType) {
        final View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item__contact, parent, false);
        return new ContactViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(final ContactViewHolder holder, final int position) {
        final User user = this.users.get(position);
        holder.setUser(user);
        holder.setOnClickListener(this);
    }

    @Override
    public int getItemCount() {
        return this.users.size();
    }

    @Override
    public void onClick(final int position) {
        if (this.onItemClickListener == null) {
            return;
        }

        final User clickedUser = users.get(position);
        this.onItemClickListener.onItemClick(clickedUser);
    }

    public ContactsAdapter setOnItemClickListener(final OnItemClickListener<User> onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
        return this;
    }

    public void clear() {
        this.users.clear();
        notifyDataSetChanged();
    }
}
