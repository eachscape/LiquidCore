//
// CustomArrayAdapter.java
// LiquidPlayer Project
//
// https://github.com/LiquidPlayer
//
// Created by Eric Lange
//
/*
 Copyright (c) 2016 Eric Lange. All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:

 - Redistributions of source code must retain the above copyright notice, this
 list of conditions and the following disclaimer.

 - Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.liquidplayer.demoapp;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.liquidplayer.service.MicroService;
import org.liquidplayer.surfaces.console.ConsoleSurface;

import java.io.File;
import java.net.URI;
import java.util.List;

/**
 * This is a custom array adapter used to populate the listview whose items will
 * expand to display extra content in addition to the default display.
 */
class CustomArrayAdapter extends ArrayAdapter<ExpandableListItem> {

    private URI consoleURI =
            URI.create("android.resource://" + getContext().getPackageName() + "/raw/webtorrent");

    private final Handler uiThread = new Handler(Looper.getMainLooper());
    private static int port = 8080;

    private class UIObject {
        ImageButton download;
        ImageView trash;
        ProgressBar progressBar;
        int position;
        ConsoleSurface consoleView;

        final Runnable setDownloadPlayButton = new Runnable() {
            @Override
            public void run() {
                if (mData.get(position).getFileName() != null) {
                    download.setImageResource(Resources.getSystem()
                            .getIdentifier("ic_media_play", "drawable", "android"));
                    download.setAlpha(1f);
                    download.setEnabled(true);
                    download.setOnClickListener(playVideo);

                    trash.setAlpha(1f);
                    trash.setEnabled(true);
                    trash.setOnClickListener(deleter);
                } else {
                    download.setImageResource(Resources.getSystem()
                            .getIdentifier("stat_sys_download", "drawable", "android"));

                    if (mData.get(position).isDownloading()) {
                        download.setAlpha(0.5f);
                        download.setEnabled(false);
                    } else {
                        download.setAlpha(1f);
                        download.setEnabled(true);
                        download.setOnClickListener(downloader);
                    }

                    trash.setAlpha(0.5f);
                    trash.setEnabled(false);
                }
            }
        };

        final View.OnClickListener playVideo = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Dialog dialog = new Dialog(CustomArrayAdapter.this.getContext());
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setContentView(R.layout.video_dialog);
                dialog.show();
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
                if (dialog.getWindow() != null) {
                    lp.copyFrom(dialog.getWindow().getAttributes());
                    dialog.getWindow().setAttributes(lp);
                }
                Uri uriPath = Uri.fromFile(new File(mData.get(position).getFileName()));

                VideoView videoView = (VideoView) dialog.findViewById(R.id.video_view);
                videoView.setVideoURI(uriPath);
                videoView.start();
            }
        };

        final View.OnClickListener deleter = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!new File(mData.get(position).getFileName()).delete()) {
                    android.util.Log.e("deleter", "Failed to delete file");
                }

                consoleView.reset();
                mData.get(position).setFileName(null);
                progressBar.setProgress(0);
                mData.get(position).setProgress(0);
                trash.setAlpha(0.5f);
                trash.setEnabled(false);
                setDownloadPlayButton.run();
            }
        };

        final View.OnClickListener downloader = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                download.setAlpha(0.5f);
                download.setEnabled(false);
                mData.get(position).setDownloading(true);
                new MicroService(getContext(), consoleURI,
                    new MicroService.ServiceStartListener() {
                        @Override
                        public void onStart(MicroService service) {
                            final MicroService.EventListener drawListener =
                                    new MicroService.EventListener() {
                                @Override
                                public void onEvent(MicroService service, String event,
                                                    JSONObject torrent) {
                                    try {
                                        final double progress = torrent.getDouble("progress");
                                        final UIObject uiObject =
                                                (UIObject) mData.get(position).getData();
                                        uiThread.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                uiObject.progressBar.setProgress(
                                                        (int) (progress * 1000));
                                                mData.get(position).setProgress(
                                                        (int) (progress * 1000));
                                            }
                                        });
                                    } catch (JSONException e) {
                                        android.util.Log.e("drawListener", "Malformed JSON");
                                    }
                                }
                            };
                            final MicroService.EventListener doneListener =
                                    new MicroService.EventListener() {
                                @Override
                                public void onEvent(MicroService service, String event,
                                                    JSONObject torrent) {
                                    mData.get(position).setDownloading(false);
                                    final UIObject uiObject =
                                            (UIObject) mData.get(position).getData();
                                    try {
                                        JSONArray files = torrent.getJSONArray("files");
                                        String fileName = service.getSharedPath().getAbsolutePath()
                                                + "/" + files.getJSONObject(0).getString("path");
                                        mData.get(position).setFileName(fileName);
                                        service.removeEventListener("torrent_done", this);
                                        service.removeEventListener("draw", drawListener);
                                        uiObject.consoleView.detach();
                                        uiThread.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                uiObject.progressBar.setProgress(1000);
                                                mData.get(position).setProgress(1000);
                                                uiObject.setDownloadPlayButton.run();
                                            }
                                        });
                                    } catch (JSONException e) {
                                        android.util.Log.e("doneListener", "Malformed JSON");
                                    }
                                }
                            };
                            service.addEventListener("torrent_done", doneListener);
                            service.addEventListener("draw", drawListener);
                            final UIObject uiObject =
                                    (UIObject) mData.get(position).getData();
                            uiObject.consoleView.attach(service);
                        }
                    },
                    new MicroService.ServiceErrorListener() {
                        @Override
                        public void onError(MicroService service, Exception e) {
                            final UIObject uiObject = (UIObject) mData.get(position).getData();
                            uiObject.consoleView.detach();

                        }
                    },
                    new MicroService.ServiceExitListener() {
                        @Override
                        public void onExit(MicroService service) {
                            final UIObject uiObject = (UIObject) mData.get(position).getData();
                            uiObject.consoleView.detach();
                        }
                    }
                )
                .start("-o", "/home/external/persistent", "-p", "" + (++port),
                        "download", mData.get(position).getUrl());
            }
        };
    }

    private List<ExpandableListItem> mData;
    private int mLayoutViewResourceId;
    private ExpandingListView mListView;

    CustomArrayAdapter(Context context, int layoutViewResourceId,
                              List<ExpandableListItem> data, ExpandingListView listView) {
        super(context, layoutViewResourceId, data);
        mData = data;
        mLayoutViewResourceId = layoutViewResourceId;
        mListView = listView;
    }

    /**
     * Populates the item in the listview cell with the appropriate data. This method
     * sets the thumbnail image, the title and the extra text. This method also updates
     * the layout parameters of the item's view so that the image and title are centered
     * in the bounds of the collapsed view, and such that the extra text is not displayed
     * in the collapsed state of the cell.
     */
    @Override
    public @NonNull View getView(final int position, View convertView, @NonNull ViewGroup parent) {

        final ExpandableListItem object = mData.get(position);
        int id = position==0 ? R.id.console1 : position==1 ? R.id.console2 : R.id.console3;
        if(convertView == null) {
            LayoutInflater inflater = ((Activity) getContext()).getLayoutInflater();
            convertView = inflater.inflate(mLayoutViewResourceId, parent, false);

            RelativeLayout linearLayout = (RelativeLayout) (convertView.findViewById(
                    R.id.item_linear_layout));
            LinearLayout.LayoutParams linearLayoutParams = new LinearLayout.LayoutParams
                    (AbsListView.LayoutParams.MATCH_PARENT, object.getCollapsedHeight());
            linearLayout.setLayoutParams(linearLayoutParams);

            convertView.setLayoutParams(new ListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT,
                    AbsListView.LayoutParams.WRAP_CONTENT));

            final LinearLayout ll = (LinearLayout) convertView.findViewById(R.id.fragment);
            ll.setId(ViewStub.generateViewId());

            ConsoleSurface consoleView = new ConsoleSurface(getContext());
            consoleView.setId(id);
            consoleView.setLayoutParams(
                    new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300,
                                    getContext().getResources().getDisplayMetrics())));

            ll.addView(consoleView);
        }

        UIObject uiObject = new UIObject();

        uiObject.position = position;
        uiObject.consoleView = (ConsoleSurface) convertView.findViewById(id);
        uiObject.download = (ImageButton) convertView.findViewById(R.id.icon);
        uiObject.trash = (ImageView) convertView.findViewById(R.id.trash);
        uiObject.progressBar = (ProgressBar) convertView.findViewById(R.id.secondLine);

        mData.get(position).setData(uiObject);

        TextView titleView = (TextView) convertView.findViewById(R.id.firstLine);

        ImageView consoleButton = (ImageView) convertView.findViewById(R.id.console);
        final View itemView = convertView;
        consoleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (object.isExpanded()) {
                    mListView.collapseView(itemView);
                } else {
                    mListView.expandView(itemView);
                }
            }
        });

        titleView.setText(object.getTitle());

        ExpandingLayout expandingLayout = (ExpandingLayout) convertView.findViewById(R.id
                .expanding_layout);

        uiObject.progressBar.setMax(1000);
        uiObject.progressBar.setProgress(object.getProgress());

        uiObject.setDownloadPlayButton.run();

        expandingLayout.setExpandedHeight(object.getExpandedHeight());
        expandingLayout.setSizeChangedListener(object);

        if (!object.isExpanded()) {
            expandingLayout.setVisibility(View.GONE);
        } else {
            expandingLayout.setVisibility(View.VISIBLE);
        }

        return convertView;
    }

}