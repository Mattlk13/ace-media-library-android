/*****************************************************************************
 * MediaItemDetailsFragment.java
 *****************************************************************************
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
 * Author: Geoffrey Métais
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/
package org.videolan.vlc.gui.tv;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.DetailsFragment;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.DetailsOverviewRow;
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnActionClickedListener;
import androidx.core.content.ContextCompat;
import android.text.TextUtils;
import android.widget.Toast;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.PlaybackServiceFragment;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity;
import org.videolan.vlc.gui.tv.browser.SortedBrowserFragment;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.FileUtils;

import java.util.List;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class MediaItemDetailsFragment extends DetailsFragment implements PlaybackService.Client.Callback {
    private static final String TAG = "MediaItemDetailsFragment";
    private static final int ID_PLAY = 1;
    private static final int ID_LISTEN = 2;
    private static final int ID_FAVORITE_ADD = 3;
    private static final int ID_FAVORITE_DELETE = 4;
    private static final int ID_BROWSE = 5;
    private static final int ID_DL_SUBS = 6;
    private static final int ID_PLAY_ALL = 7;
    private static final int ID_PLAY_FROM_START = 8;
    private static final int ID_OPEN_IN_PLAYER = 9;

    private BackgroundManager mBackgroundManager;
    private ArrayObjectAdapter mRowsAdapter;
    private MediaItemDetails mMedia;
    private MediaWrapper mMediaWrapper;
    private MediaDatabase mDb;
    private PlaybackService mService;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBackgroundManager = BackgroundManager.getInstance(getActivity());
        mBackgroundManager.setAutoReleaseOnStop(false);
        buildDetails();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mBackgroundManager.isAttached())
            mBackgroundManager.attachToView(getView());
    }

    public void onPause() {
        TvUtil.releaseBackgroundManager(mBackgroundManager);
        super.onPause();
        if (mService != null && mService.isPlaying())
            mService.stop();
    }

    @Override
    public void onStop() {
        super.onStop();
        PlaybackServiceFragment.unregisterPlaybackService(this, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void buildDetails() {
        Bundle extras = getActivity().getIntent().getExtras();
        mMedia = extras.getParcelable("item");
        boolean hasMedia = extras.containsKey("media");
        ClassPresenterSelector selector = new ClassPresenterSelector();
        final MediaWrapper media = hasMedia ? (MediaWrapper) extras.getParcelable("media") : new MediaWrapper(AndroidUtil.LocationToUri(mMedia.getLocation()));
        if (!hasMedia){
            media.setDisplayTitle(mMedia.getTitle());
        }
        mMediaWrapper = media;
        setTitle(media.getTitle());

        final List<MediaWrapper> mediaList = (List<MediaWrapper>) VLCApplication.getData(SortedBrowserFragment.CURRENT_BROWSER_LIST);
        // Attach your media item details presenter to the row presenter:
        FullWidthDetailsOverviewRowPresenter rowPresenter = new FullWidthDetailsOverviewRowPresenter(new DetailsDescriptionPresenter());

        final Activity activity = getActivity();
        final DetailsOverviewRow detailsOverview = new DetailsOverviewRow(mMedia);
        final Action actionAdd = new Action(ID_FAVORITE_ADD, getString(R.string.favorites_add));
        final Action actionDelete = new Action(ID_FAVORITE_DELETE, getString(R.string.favorites_remove));

        rowPresenter.setBackgroundColor(ContextCompat.getColor(activity, R.color.orange500));
        rowPresenter.setOnActionClickedListener(new OnActionClickedListener() {

            @Override
            public void onActionClicked(Action action) {
                switch ((int)action.getId()){
                    case ID_LISTEN:
                        PlaybackServiceFragment.registerPlaybackService(MediaItemDetailsFragment.this, MediaItemDetailsFragment.this);
                        break;
                    case ID_PLAY:
                        TvUtil.playMedia(getActivity(), media);
                        getActivity().finish();
                        break;
                    case ID_FAVORITE_ADD:
                        mDb.addNetworkFavItem(Uri.parse(mMedia.getLocation()), mMedia.getTitle(), mMedia.getArtworkUrl());
                        detailsOverview.removeAction(actionAdd);
                        detailsOverview.addAction(actionDelete);
                        mRowsAdapter.notifyArrayItemRangeChanged(0, mRowsAdapter.size());
                        Toast.makeText(VLCApplication.getAppContext(), R.string.favorite_added, Toast.LENGTH_SHORT).show();
                        break;
                    case ID_FAVORITE_DELETE:
                        mDb.deleteNetworkFav(Uri.parse(mMedia.getLocation()));
                        detailsOverview.removeAction(actionDelete);
                        detailsOverview.addAction(actionAdd);
                        mRowsAdapter.notifyArrayItemRangeChanged(0, mRowsAdapter.size());
                        Toast.makeText(VLCApplication.getAppContext(), R.string.favorite_removed, Toast.LENGTH_SHORT).show();
                        break;
                    case ID_BROWSE:
                        TvUtil.openMedia(getActivity(), media, null);
                        break;
                    case ID_DL_SUBS:
                        MediaUtils.getSubs(getActivity(), media);
                        break;
                    case ID_PLAY_ALL:
                        if (mediaList != null) {
                            int position = -1;
                            for (int i= 0; i < mediaList.size(); ++i)
                                if (media.equals(mediaList.get(i)))
                                    position = i;
                            Activity activity = getActivity();
                            MediaUtils.openList(activity, mediaList, position);
                            if (media.getType() == MediaWrapper.TYPE_AUDIO)
                                getActivity().startActivity(new Intent(activity, AudioPlayerActivity.class));
                            getActivity().finish();
                        }
                        break;
                    case ID_PLAY_FROM_START:
                        VideoPlayerActivity.start(getActivity(), media.getUri(), true);
                        getActivity().finish();
                        break;
                    case ID_OPEN_IN_PLAYER:
                        MediaUtils.showPlayerSelector(getActivity(), media);
                        break;
                }
            }
        });
        selector.addClassPresenter(DetailsOverviewRow.class, rowPresenter);
        selector.addClassPresenter(ListRow.class,
                new ListRowPresenter());
        mRowsAdapter = new ArrayObjectAdapter(selector);
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final Bitmap cover = media.getType() == MediaWrapper.TYPE_AUDIO || media.getType() == MediaWrapper.TYPE_VIDEO
                ? AudioUtil.readCoverBitmap(mMedia.getArtworkUrl(), 512) : null;
                VLCApplication.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isDetached())
                            return;
                        if (media.getType() == MediaWrapper.TYPE_DIR && FileUtils.canSave(media)) {
                            mDb = MediaDatabase.getInstance();
                            detailsOverview.setImageDrawable(ContextCompat.getDrawable(activity, TextUtils.equals(media.getUri().getScheme(),"file")
                                    ? R.drawable.ic_menu_folder_big
                                    : R.drawable.ic_menu_network_big));
                            detailsOverview.setImageScaleUpAllowed(true);
                            detailsOverview.addAction(new Action(ID_BROWSE, getString(R.string.browse_folder)));
                            if (mDb.networkFavExists(Uri.parse(mMedia.getLocation())))
                                detailsOverview.addAction(actionDelete);
                            else
                                detailsOverview.addAction(actionAdd);

                        } else if (media.getType() == MediaWrapper.TYPE_AUDIO) {
                            // Add images and action buttons to the details view
                            if (cover == null)
                                detailsOverview.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.ic_acestream));
                            else
                                detailsOverview.setImageBitmap(getActivity(), cover);

                            detailsOverview.addAction(new Action(ID_PLAY, getString(R.string.play)));
                            detailsOverview.addAction(new Action(ID_LISTEN, getString(R.string.listen)));
                            if (mediaList != null && mediaList.size() > 1 && mediaList.contains(media))
                                detailsOverview.addAction(new Action(ID_PLAY_ALL, getString(R.string.play_all)));
                        } else if (media.getType() == MediaWrapper.TYPE_VIDEO) {
                            // Add images and action buttons to the details view
                            if (cover == null)
                                detailsOverview.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.ic_acestream));
                            else
                                detailsOverview.setImageBitmap(getActivity(), cover);

                            detailsOverview.addAction(new Action(ID_PLAY, getString(R.string.play)));
                            detailsOverview.addAction(new Action(ID_PLAY_FROM_START, getString(R.string.play_from_start)));
                            if (FileUtils.canWrite(media.getUri()))
                                detailsOverview.addAction(new Action(ID_DL_SUBS, getString(R.string.download_subtitles)));
                            if (mediaList != null && mediaList.size() > 1 && mediaList.contains(media))
                                detailsOverview.addAction(new Action(ID_PLAY_ALL, getString(R.string.play_all)));
                            if(media.isP2PItem())
                                detailsOverview.addAction(new Action(ID_OPEN_IN_PLAYER, getString(R.string.select_player)));
                        }
                        mRowsAdapter.add(detailsOverview);
                        setAdapter(mRowsAdapter);
                        if (cover != null)
                            mBackgroundManager.setBitmap(cover);
                    }
                });
            }
        });
    }

    @Override
    public void onConnected(PlaybackService service) {
        mService = service;
        mService.load(mMediaWrapper);
    }

    @Override
    public void onDisconnected() {
        mService = null;
    }
}
