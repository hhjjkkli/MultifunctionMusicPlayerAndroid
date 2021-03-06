/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.uamp.model;

import android.graphics.Bitmap;
import android.support.v4.media.MediaMetadataCompat;

import com.example.android.uamp.utils.BitmapHelper;
import com.example.android.uamp.utils.LogHelper;
import com.example.android.uamp.utils.LyricHelper;
import com.example.android.uamp.utils.MediaIDHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static android.media.MediaMetadata.METADATA_KEY_TITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE;

/**
 * Utility class to get a list of MusicTrack's based on a server-side JSON
 * configuration.
 */
public class RemoteJSONSource implements MusicProviderSource {
    public static int mCoverFlowSize = 1;
    public static boolean isIconDownloaded = false;
    public static LinkedHashMap<String[], Bitmap> mMusicMap = new LinkedHashMap<>();

    private static final String TAG = LogHelper.makeLogTag(RemoteJSONSource.class);

    //protected static final String CATALOG_URL =
        //"http://storage.googleapis.com/automotive-media/music.json";
    //改为自己的服务器
    protected static final String CATALOG_URL =
            "http://111.230.145.180:7788/music-media/music.json";

    private static final String JSON_MUSIC = "music";
    private static final String JSON_TITLE = "title";
    private static final String JSON_ALBUM = "album";
    private static final String JSON_ARTIST = "artist";
    private static final String JSON_GENRE = "genre";
    private static final String JSON_SOURCE = "source";
    private static final String JSON_IMAGE = "image";
    //添加新内容：歌词
    private static String g_lyric = null;
    private static final String JSON_LYRIC = "lyric";
    private static final String JSON_TRACK_NUMBER = "trackNumber";
    private static final String JSON_TOTAL_TRACK_COUNT = "totalTrackCount";
    private static final String JSON_DURATION = "duration";

    @Override
    public Iterator<MediaMetadataCompat> iterator() {
        try {
            int slashPos = CATALOG_URL.lastIndexOf('/');
            String path = CATALOG_URL.substring(0, slashPos + 1);
            //从http协议下载music.json文件
            JSONObject jsonObj = fetchJSONFromUrl(CATALOG_URL);
            final ArrayList<MediaMetadataCompat> tracks = new ArrayList<>();
            if (jsonObj != null) {
                //保存音乐信息的数组
                JSONArray jsonTracks = jsonObj.getJSONArray(JSON_MUSIC);

                if (jsonTracks != null) {
                    for (int j = 0; j < jsonTracks.length(); j++) {
                        //path == "http://storage.googleapis.com/" 从这个服务器下载MP3文件
                        tracks.add(buildFromJSON(jsonTracks.getJSONObject(j), path));
                    }
                }
                mCoverFlowSize = jsonTracks.length();
            }
            //启动下载线程下载图片存到全局变量里去
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    RemoteJSONSource.isIconDownloaded = false;
                    int i = 0;
                    while (i < 10) {
                        if (i >= tracks.size()) {
                            break;
                        }

                        String[] key = new String[]{null, null};
                        Bitmap bmp = null;
                        String musicId = null;
                        MediaMetadataCompat metadata = tracks.get(i);
                        String iconUrl = metadata.getString(METADATA_KEY_ALBUM_ART_URI);
                        String asciiUrl=null;
                        try {
                            asciiUrl = new URI(iconUrl).toASCIIString();
                        } catch (URISyntaxException e) {
                            e.printStackTrace();
                        }
                        try {
                            bmp = BitmapHelper.fetchAndRescaleBitmap(asciiUrl, 800, 480);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        // = "test" + i;
                        musicId = MediaIDHelper.createMediaID(
                                metadata.getDescription().getMediaId(), MEDIA_ID_MUSICS_BY_GENRE, metadata.getString(MediaMetadataCompat.METADATA_KEY_GENRE));
                        key[0] = musicId;
                        key[1] = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE);

                        RemoteJSONSource.mMusicMap.put(key, bmp);
                        i++;
                    }
                    RemoteJSONSource.isIconDownloaded = true;
                }
            });
            t.start();

            return tracks.iterator();
        } catch (JSONException e) {
            LogHelper.e(TAG, e, "Could not retrieve music list");
            throw new RuntimeException("Could not retrieve music list", e);
        }
    }

    //json.getString()会让中文乱码
    private MediaMetadataCompat buildFromJSON(JSONObject json, String basePath) throws JSONException {
        String title = json.getString(JSON_TITLE);
        String album = json.getString(JSON_ALBUM);
        String artist = json.getString(JSON_ARTIST);
        String genre = json.getString(JSON_GENRE);
        String source = json.getString(JSON_SOURCE);
        String iconUrl = json.getString(JSON_IMAGE);
        g_lyric = json.getString(JSON_LYRIC);
        int trackNumber = json.getInt(JSON_TRACK_NUMBER);
        int totalTrackCount = json.getInt(JSON_TOTAL_TRACK_COUNT);
        int duration = json.getInt(JSON_DURATION) * 1000; // ms

        LogHelper.d(TAG, "Found music track: ", json);

        // Media is stored relative to JSON file
        if (!source.startsWith("http")) {
            source = basePath + source;
        }
        if (!iconUrl.startsWith("http")) {
            iconUrl = basePath + iconUrl;
        }
        if (!g_lyric.startsWith("http")) {
            g_lyric = basePath + g_lyric;
        }
        // 获取歌词文件路径，下载
        //isFetched = false;

        /*while (isFetched == false) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // 等待下载完成
        }*/
        // Since we don't have a unique ID in the server, we fake one using the hashcode of
        // the music source. In a real world app, this could come from the server.
        String id = String.valueOf(source.hashCode());

        // Adding the music source to the MediaMetadata (and consequently using it in the
        // mediaSession.setMetadata) is not a good idea for a real world music app, because
        // the session metadata can be accessed by notification listeners. This is done in this
        // sample for convenience only.
        //noinspection ResourceType
        // MediaMetadataCompat里面竟然没有lyric这一项，所以先用subtitle这一项
        return new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, id)
                .putString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE, source)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                .putString(MediaMetadataCompat.METADATA_KEY_GENRE, genre)
                .putString(METADATA_KEY_ALBUM_ART_URI, iconUrl)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, g_lyric)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, trackNumber)
                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, totalTrackCount)
                .build();
    }

    /**
     * Download a JSON file from a server, parse the content and return the JSON
     * object.
     *
     * @return result JSONObject containing the parsed representation.
     */
    private JSONObject fetchJSONFromUrl(String urlString) throws JSONException {
        BufferedReader reader = null;
        try {
            //用http协议将json文本文件下载到内存转化为String对象
            URLConnection urlConnection = new URL(urlString).openConnection();
            reader = new BufferedReader(new InputStreamReader(
                    urlConnection.getInputStream(), "utf-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return new JSONObject(sb.toString());
        } catch (JSONException e) {
            throw e;
        } catch (Exception e) {
            LogHelper.e(TAG, "Failed to parse the json for media list", e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}
