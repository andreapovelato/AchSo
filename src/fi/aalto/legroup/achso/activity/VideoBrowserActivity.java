/**
 * Copyright 2013 Aalto university, see AUTHORS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fi.aalto.legroup.achso.activity;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.util.Pair;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import fi.aalto.legroup.achso.R;
import fi.aalto.legroup.achso.adapter.BrowsePagerAdapter;
import fi.aalto.legroup.achso.adapter.SearchPagerAdapter;
import fi.aalto.legroup.achso.database.SemanticVideo;
import fi.aalto.legroup.achso.database.VideoDBHelper;
import fi.aalto.legroup.achso.fragment.BrowseFragment;
import fi.aalto.legroup.achso.fragment.VideoViewerFragment;
import fi.aalto.legroup.achso.state.IntentDataHolder;
import fi.aalto.legroup.achso.state.i5LoginState;
import fi.aalto.legroup.achso.upload.UploaderService;
import fi.aalto.legroup.achso.util.App;
import fi.aalto.legroup.achso.util.SearchResultCache;
import fi.google.zxing.integration.android.IntentIntegrator;
import fi.google.zxing.integration.android.IntentResult;

public class VideoBrowserActivity extends ActionbarActivity implements BrowseFragment.Callbacks,
        ViewPager.OnPageChangeListener {

    private List<SemanticVideo> mSelectedVideosForQrCode;
    private UploaderBroadcastReceiver mReceiver = null;
    private IntentFilter mFilter = null;
    private String mQrResult = null;
    private String mQuery = null;
    private FragmentStatePagerAdapter mPagerAdapter;
    private ViewPager mViewPager;
    private SearchView mSearchView;
    protected boolean show_record() {return true;}
    protected boolean show_login() {return true;}
    protected boolean show_qr() {return true;}
    protected boolean show_search() {return true;}

    public static boolean isTablet(Context ctx) {
        return (ctx.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    public void setSelectedVideosForQrCode(HashMap<Integer, SemanticVideo> videos) {
        mSelectedVideosForQrCode = new ArrayList<SemanticVideo>();
        for (SemanticVideo v : videos.values()) {
            mSelectedVideosForQrCode.add(v);
        }
    }

    /**
     * Creates browsing top menu and does all other initialization for browsing/search views.
     * @param menu
     * @return
     */
    @Override
    @SuppressLint("NewApi")
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i("VideoBrowserActivity", "Inflating options menu - VideoBrowserActivity");
        App.login_state.autologinIfAllowed();
        initMenu(menu);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        final MenuItem si = menu.findItem(R.id.action_search);
        assert (si != null);
        mSearchView = (SearchView) si.getActionView();
        mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        mSearchView.setIconifiedByDefault(true);
        final Context ctx = this;
        final SearchView sv = mSearchView;

        mSearchView.setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    sv.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(sv.getApplicationWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            si.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    mPagerAdapter = new BrowsePagerAdapter(ctx, getSupportFragmentManager());
                    mViewPager.setAdapter(mPagerAdapter); // with pagerAdapter the set and getadapters should be same
                    mPagerAdapter.notifyDataSetChanged();
                    mQuery = null;
                    return true;
                }
            });
        } else {// Not 100% sure that this works on api lvl 11
            mSearchView.setOnCloseListener(new SearchView.OnCloseListener() {
                @Override
                public boolean onClose() {
                    mPagerAdapter = new BrowsePagerAdapter(ctx, getSupportFragmentManager());
                    mViewPager.setAdapter(mPagerAdapter);
                    mPagerAdapter.notifyDataSetChanged();
                    mQuery = null;
                    return true;
                }
            });
        }
        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    /**
     * Main concern here is to set up filters so that the browse page can react to background
     * processes and system events.
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_mainmenu);

        if (mFilter == null || mReceiver == null) {
            mFilter = new IntentFilter();
            mFilter.addAction(UploaderBroadcastReceiver.UPLOAD_START_ACTION);
            mFilter.addAction(UploaderBroadcastReceiver.UPLOAD_PROGRESS_ACTION);
            mFilter.addAction(UploaderBroadcastReceiver.UPLOAD_END_ACTION);
            mFilter.addAction(i5LoginState.LOGIN_SUCCESS);
            mFilter.addAction(i5LoginState.LOGIN_FAILED);
            mFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            //mFilter.addCategory(Intent.CATEGORY_DEFAULT);
            mReceiver = new UploaderBroadcastReceiver();
        }

        if (mQuery == null) {
            mViewPager = (ViewPager) findViewById(R.id.pager);
            mPagerAdapter = new BrowsePagerAdapter(this, getSupportFragmentManager());
        } else {
            mPagerAdapter = new SearchPagerAdapter(this, getSupportFragmentManager(), mQuery, true);
        }
        mViewPager.setAdapter(mPagerAdapter);
        mViewPager.setOnPageChangeListener(this);

        handleIntent(getIntent());

        App.getLocation();
        //final LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        //if (!(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager
        //        .isProviderEnabled(LocationManager.NETWORK_PROVIDER))) {
            //getLocationNotEnabledDialog().show();
        //}

    }


    @Override
    public void onItemSelected(long id) {
        Intent detailIntent = new Intent(this, VideoViewerActivity.class);
        detailIntent.putExtra(VideoViewerFragment.ARG_ITEM_ID, id);
        startActivity(detailIntent);
    }

    @Override
    public void onRemoteItemSelected(int positionInCache) {
        Intent detailIntent = new Intent(this, VideoViewerActivity.class);
        detailIntent.putExtra(VideoViewerFragment.ARG_ITEM_CACHE_POSITION, positionInCache);
        startActivity(detailIntent);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        // ActionbarActivity handles REQUEST_VIDEO_CAPTURE,
        // REQUEST_LOGIN and REQUEST_LOCATION_SERVICES.
        // The ones handled here affect the search results. (Note that the actual searching and
        // browsing is not done by calling activities/ using intents,
        // they are more about manipulating fragments here.
        super.onActivityResult(requestCode, resultCode, intent);
            switch (requestCode) {
                // Last activity was genre selection
                case REQUEST_SEMANTIC_VIDEO_GENRE:
                    if (resultCode == RESULT_OK) {
                        // Update the list view as we have more data
                        VideoDBHelper vdb = new VideoDBHelper(this);
                        vdb.updateVideoCache();
                        vdb.close();
                        if (intent == null) {
                            Log.i("itemListActivity", "something failed: camera resulted an empty intent.");
                        } else {
                            Toast.makeText(this, "Created SemanticVideo with id: " + intent.getLongExtra("video_id", -1), Toast.LENGTH_LONG).show();
                        }
                    } else {
                        // Go back to video recording if we got back from genre selection.
                        launchRecording();
                    }
                    break;
                case IntentIntegrator.REQUEST_CODE:
                    IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
                    if (scanResult != null) {
                        if (IntentDataHolder.From == ActionbarActivity.class) {
                            if (!scanResult.getContents().equals(mQuery)) {
                                mQuery = scanResult.getContents();
                                SearchResultCache.clearLastSearch();
                            }
                            // switch the page to show search results
                            // last argument in next line is 'isItTitleQuery'. false means qr-query.
                            mPagerAdapter = new SearchPagerAdapter(this, getSupportFragmentManager(), mQuery, false);
                            mViewPager.setAdapter(mPagerAdapter);
                            mPagerAdapter.notifyDataSetChanged();
                            ActionBar bar = getActionBar();
                            if (bar != null) {
                                bar.setDisplayHomeAsUpEnabled(true); // Enable back button
                            }
                        }
                        /** not used currently, but keep the code if we need to do this
                         *
                         * else if (scanResult.getContents() != null
                                && IntentDataHolder.From == SemanticVideoPlayerFragment.class) {
                            mQrResult = scanResult.getContents();
                        }
                         */
                    }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        this.unregisterReceiver(mReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        invalidateOptionsMenu();
        this.registerReceiver(mReceiver, mFilter);
        if (mQuery != null) {
            mSearchView.setQuery(mQuery, false);
            mSearchView.clearFocus();
        }
        if (mQrResult != null) {
            VideoDBHelper vdb = new VideoDBHelper(this);
            for (SemanticVideo v : mSelectedVideosForQrCode) {
                // Again, a place for database optimization.
                v.setQrCode(mQrResult);
                vdb.update(v);
            }
            vdb.close();
            Toast.makeText(this, "Qr-code: " + mQrResult + " added to selected video(s).", Toast.LENGTH_LONG).show();
            mQrResult = null;
        }
    }

    private Pair<ProgressBar, ImageView> getViewsForUploadUi(SemanticVideo sv) {
        BrowseFragment f = (BrowseFragment) mPagerAdapter.getItem(mViewPager.getCurrentItem());

        if (f == null || !f.isAdded())
            return null;
        ListAdapter la = f.getListAdapter();
        int listitemcount = la.getCount();
        int pos = 0;
        for (int i = 0; i < listitemcount; ++i) {
            if (la.getItem(i) == sv) {
                pos = i;
                break;
            }
        }
        View v;
        if (isTablet(this)) {
            v = ((GridView) f.getView().findViewById(R.id.main_menu_grid)).getChildAt(pos);
        } else {
            v = ((ListView) f.getView().findViewById(R.id.browse_list)).getChildAt(pos);
        }
        if (v == null)
            return null;
        ProgressBar pb = (ProgressBar) v.findViewById(R.id.upload_progress);
        ImageView uploadIcon = (ImageView) v.findViewById(R.id.upload_icon);

        return new Pair(pb, uploadIcon);
    }

    @Override
    public void onPageScrolled(int i, float v, int i2) {

    }

    @Override
    public void onPageSelected(int i) {
        int k = Math.max(i - 1, 0);
        int m = Math.min(i + 1, mPagerAdapter.getCount() - 1);
        for (int j = k; j <= m; ++j) { // Finish action modes on both sides of
            // current fragment
            ActionMode am = ((BrowseFragment) mPagerAdapter.getItem(j)).getActionMode();
            if (am != null)
                am.finish();
        }
    }

    @Override
    public void onPageScrollStateChanged(int i) {

    }

    @Override
    protected void onNewIntent(Intent i) {
        handleIntent(i);
    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            if (!intent.getStringExtra(SearchManager.QUERY).equals(mQuery)) {
                mQuery = intent.getStringExtra(SearchManager.QUERY);
                SearchResultCache.clearLastSearch();
            }
            mPagerAdapter = new SearchPagerAdapter(this, getSupportFragmentManager(), mQuery, true);
            mViewPager.setAdapter(mPagerAdapter);
            mViewPager.getAdapter().notifyDataSetChanged();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                mPagerAdapter = new BrowsePagerAdapter(getApplicationContext(), getSupportFragmentManager());
                mViewPager.setAdapter(mPagerAdapter);
                mViewPager.getAdapter().notifyDataSetChanged();
                if (mViewPager.getAdapter() != mPagerAdapter) {
                    Log.e("VideoBrowserActivity", "set and get adapter differ");
                }
                mQuery = null;
                getActionBar().setDisplayHomeAsUpEnabled(false); // Disable back
                // button after
                // reverting
                // from search
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        App.appendLog("Closing Ach So!");
        super.onDestroy();
    }

    public class UploaderBroadcastReceiver extends AchSoBroadcastReceiver {
        public static final String UPLOAD_START_ACTION = "fi.aalto.legroup.achso.intent.action.UPLOAD_START";
        public static final String UPLOAD_PROGRESS_ACTION = "fi.aalto.legroup.achso.intent.action.UPLOAD_PROGRESS";
        public static final String UPLOAD_END_ACTION = "fi.aalto.legroup.achso.intent.action.UPLOAD_END";
        public static final String UPLOAD_ERROR_ACTION = "fi.aalto.legroup.achso.intent.action.UPLOAD_END";

        @Override
        public void onReceive(Context context, Intent intent) {
            super.onReceive(context, intent);
            String action = intent.getAction();
            if (action != null && (action.equals(UPLOAD_START_ACTION) || action.equals(UPLOAD_START_ACTION) ||
                action.equals(UPLOAD_START_ACTION) || action.equals(UPLOAD_START_ACTION))) {

                long id = intent.getLongExtra(UploaderService.PARAM_OUT, -1);
                if (id != -1) {
                    SemanticVideo sv = VideoDBHelper.getById(id);
                    Pair<ProgressBar, ImageView> ui = getViewsForUploadUi(sv);
                    if (ui == null)
                        return;
                    int what = intent.getIntExtra(UploaderService.PARAM_WHAT, -1);
                    switch (what) {
                        case UploaderService.UPLOAD_START:
                            sv.setUploaded(false);
                            sv.setUploading(true);
                            ui.first.setVisibility(View.VISIBLE);
                            ui.second.setColorFilter(getResources().getColor(R.color.upload_icon_uploading));
                            break;
                        case UploaderService.UPLOAD_PROGRESS:
                            int percentage = intent.getIntExtra(UploaderService.PARAM_ARG, 0);
                            ui.first.setProgress(percentage);
                            break;
                        case UploaderService.UPLOAD_END:
                            sv.setUploaded(true);
                            sv.setUploading(false);
                            sv.setUploadPending(false);

                            VideoDBHelper vdb = new VideoDBHelper(context);
                            vdb.update(sv);
                            vdb.close();

                            ui.first.setVisibility(View.GONE);
                            ui.second.setVisibility(View.GONE);

                            Toast.makeText(getApplicationContext(), "Upload successful.", Toast.LENGTH_LONG).show();
                            break;
                        case UploaderService.UPLOAD_ERROR:
                            sv.setUploaded(false);
                            sv.setUploading(false);
                            sv.setUploadPending(false);
                            ui.first.setVisibility(View.GONE);
                            ui.second.setVisibility(View.GONE);
                            String errmsg = intent.getStringExtra(UploaderService.PARAM_ARG);
                            Toast.makeText(getApplicationContext(), errmsg, Toast.LENGTH_LONG).show();
                            break;
                    }
                }
            }
        }
    }
}
