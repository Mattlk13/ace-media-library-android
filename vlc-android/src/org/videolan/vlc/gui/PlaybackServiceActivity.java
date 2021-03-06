/*
 * *************************************************************************
 *  PlaybackServiceActivity.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors, VideoLAN, and VideoLabs
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui;

import android.content.Context;
import androidx.annotation.MainThread;
import androidx.fragment.app.FragmentActivity;
import android.util.Log;

import org.acestream.sdk.utils.PermissionUtils;
import org.videolan.vlc.PlaybackService;

import java.util.ArrayList;
import java.util.List;

public class PlaybackServiceActivity extends FragmentActivity implements PlaybackService.Client.Callback {
    private final static String TAG = "AS/VLC/PSActivity";
    final protected Helper mHelper = new Helper(this, this);
    protected PlaybackService mService;

    @Override
    protected void onStart() {
        super.onStart();
        if(PermissionUtils.hasStorageAccess()) {
            mHelper.onStart();
        }
        else {
            Log.w(TAG, "onStart: skip start, no storage access");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mHelper.onStop();
    }

    public Helper getHelper() {
        return mHelper;
    }

    @Override
    public void onConnected(PlaybackService service) {
        mService = service;
    }

    @Override
    public void onDisconnected() {
        mService = null;
    }

    public static class Helper {
        private List<PlaybackService.Client.Callback> mFragmentCallbacks = new ArrayList<PlaybackService.Client.Callback>();
        final private PlaybackService.Client.Callback mActivityCallback;
        private PlaybackService.Client mClient;
        protected PlaybackService mService;

        public Helper(Context context, PlaybackService.Client.Callback activityCallback) {
            mClient = new PlaybackService.Client(context, mClientCallback);
            mActivityCallback = activityCallback;
        }

        @MainThread
        public void registerFragment(PlaybackService.Client.Callback connectCb) {
            if (connectCb == null)
                throw new IllegalArgumentException("connectCb can't be null");
            mFragmentCallbacks.add(connectCb);
            if (mService != null)
                connectCb.onConnected(mService);

        }

        @MainThread
        public void unregisterFragment(PlaybackService.Client.Callback connectCb) {
            if (mService != null)
                connectCb.onDisconnected();
            mFragmentCallbacks.remove(connectCb);
        }

        @MainThread
        public void onStart() {
            mClient.connect();
        }

        @MainThread
        public void onStop() {
            mClientCallback.onDisconnected();
            mClient.disconnect();
        }

        private final  PlaybackService.Client.Callback mClientCallback = new PlaybackService.Client.Callback() {
            @Override
            public void onConnected(PlaybackService service) {
                mService = service;
                mActivityCallback.onConnected(service);
                for (PlaybackService.Client.Callback connectCb : mFragmentCallbacks)
                    connectCb.onConnected(mService);
            }

            @Override
            public void onDisconnected() {
                mService = null;
                mActivityCallback.onDisconnected();
                for (PlaybackService.Client.Callback connectCb : mFragmentCallbacks)
                    connectCb.onDisconnected();
            }
        };
    }
}