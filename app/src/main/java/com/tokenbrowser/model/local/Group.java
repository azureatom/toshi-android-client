/*
 * 	Copyright (c) 2017. Token Browser, Inc
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.tokenbrowser.model.local;


import android.graphics.Bitmap;
import android.support.annotation.NonNull;

import org.spongycastle.util.encoders.Hex;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.PrimaryKey;

public class Group extends RealmObject {
    @PrimaryKey
    private String id;
    private String title;
    private RealmList<User> members;

    @Ignore
    private Avatar avatar;

    public Group(){}

    public Group(final List<User> members) {
        this.id = generateId();
        this.members = new RealmList<>();
        this.members.addAll(members);
    }

    @NonNull
    public String getId() {
        return this.id;
    }

    @NonNull
    public byte[] getIdBytes() {
        return Hex.decode(this.id);
    }

    @NonNull
    public String getTitle() {
        return this.title == null ? "" : this.title;
    }

    @NonNull
    public List<User> getMembers() {
        if (this.members == null) {
            return Collections.emptyList();
        }
        return this.members;
    }

    public Group setTitle(final String title) {
        this.title = title;
        return this;
    }

    public Avatar getAvatar() {
        return this.avatar;
    }

    public Group setAvatar(final Bitmap avatar) {
        this.avatar = new Avatar(avatar);
        return this;
    }

    private String generateId() {
        try {
            byte[] groupId = new byte[16];
            SecureRandom.getInstance("SHA1PRNG").nextBytes(groupId);
            return Hex.toHexString(groupId);
        } catch (final NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    // Helper functions
    public List<String> getMemberIds() {
        if (this.members == null) {
            return Collections.emptyList();
        }
        final List<String> ids = new LinkedList<>();
        for (final User member : this.members) {
            ids.add(member.getTokenId());
        }
        return ids;
    }

    public List<SignalServiceAddress> getMemberAddresses() {
        if (this.members == null) {
            return Collections.emptyList();
        }
        final List<SignalServiceAddress> ids = new LinkedList<>();
        for (final User member : this.members) {
            ids.add(new SignalServiceAddress(member.getTokenId()));
        }
        return ids;
    }
}
