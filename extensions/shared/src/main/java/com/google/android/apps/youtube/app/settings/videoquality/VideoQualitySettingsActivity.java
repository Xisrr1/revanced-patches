package com.google.android.apps.youtube.app.settings.videoquality;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceScreen;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;
import android.widget.Toolbar;
import app.revanced.extension.shared.utils.Logger;
import app.revanced.extension.shared.utils.ResourceUtils;
import app.revanced.extension.shared.utils.Utils;
import app.revanced.extension.youtube.settings.preference.ReVancedPreferenceFragment;
import app.revanced.extension.youtube.utils.ThemeUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

@SuppressWarnings("deprecation")
public class VideoQualitySettingsActivity extends Activity {

    public static String searchLabel;
    public static WeakReference<SearchView> searchViewRef = new WeakReference<>(null);
    public static String rvxSettingsLabel;
    private static ViewGroup.LayoutParams lp;
    private Toolbar toolbar;
    private ReVancedPreferenceFragment fragment;
    private final OnQueryTextListener onQueryTextListener = new OnQueryTextListener() {
        @Override
        public boolean onQueryTextSubmit(String query) {
            Logger.printDebug(() -> "onQueryTextSubmit called with: " + query);
            String queryTrimmed = query.trim();
            if (!queryTrimmed.isEmpty()) {
                fragment.setPreferenceScreen(fragment.rootPreferenceScreen);
                fragment.filterPreferences(queryTrimmed);
                fragment.addToSearchHistory(queryTrimmed);
                Logger.printDebug(() -> "Added to search history: " + queryTrimmed);
            }

            // Hide keyboard and remove focus
            SearchView searchView = searchViewRef.get();
            if (searchView != null) {
                searchView.clearFocus();

                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(searchView.getWindowToken(), 0);
                }
            }

            return true;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            fragment.setPreferenceScreen(fragment.rootPreferenceScreen);
            fragment.filterPreferences(newText);
            return true;
        }
    };

    public static void setToolbarLayoutParams(Toolbar toolbar) {
        if (lp != null) {
            toolbar.setLayoutParams(lp);
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Utils.getLocalizedContext(base));
    }

    @Override
    public void onBackPressed() {
        SearchView searchView = searchViewRef.get();
        String currentQuery = (searchView != null) ? searchView.getQuery().toString() : "";

        if (fragment.getPreferenceScreen() == fragment.rootPreferenceScreen) {
            if (!currentQuery.isEmpty()) {
                searchView.setQuery("", false);
                searchView.clearFocus();
                fragment.resetPreferences(); // Reset to original preferences
            } else {
                super.onBackPressed();
            }
        } else {
            // Handle sub-screen navigation
            if (!fragment.preferenceScreenStack.isEmpty()) {
                PreferenceScreen previous = fragment.preferenceScreenStack.pop();
                fragment.setPreferenceScreen(previous);
                if (!currentQuery.isEmpty()) {
                    fragment.filterPreferences(currentQuery); // Restore search results
                }
            } else {
                super.onBackPressed();
            }
        }
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        try {
            // Set fragment theme
            setTheme(ThemeUtils.getThemeId());

            // Set content
            setContentView(ResourceUtils.getLayoutIdentifier("revanced_settings_with_toolbar"));

            String dataString = getIntent().getDataString();
            if (dataString == null) {
                Logger.printException(() -> "DataString is null");
                return;
            } else if (dataString.equals("revanced_extended_settings_intent")) {
                fragment = new ReVancedPreferenceFragment();
            } else {
                Logger.printException(() -> "Unknown setting: " + dataString);
                return;
            }

            // Set label
            rvxSettingsLabel = getString("revanced_extended_settings_title");
            searchLabel = getString("revanced_extended_settings_search_title");

            // Set toolbar
            setToolbar();

            getFragmentManager()
                    .beginTransaction()
                    .replace(ResourceUtils.getIdIdentifier("revanced_settings_fragments"), fragment)
                    .commit();

            setSearchView();
        } catch (Exception ex) {
            Logger.printException(() -> "onCreate failure", ex);
        }
    }

    @SuppressLint("DiscouragedApi")
    private String getString(String str) {
        Context baseContext = getBaseContext();
        Resources resources = baseContext.getResources();
        int identifier = resources.getIdentifier(str, "string", baseContext.getPackageName());
        return resources.getString(identifier);
    }

    private void setToolbar() {
        ViewGroup toolBarParent = findViewById(ResourceUtils.getIdIdentifier("revanced_toolbar_parent"));

        // Remove dummy toolbar.
        ViewGroup dummyToolbar = toolBarParent.findViewById(ResourceUtils.getIdIdentifier("revanced_toolbar"));
        lp = dummyToolbar.getLayoutParams();
        toolBarParent.removeView(dummyToolbar);

        toolbar = new Toolbar(toolBarParent.getContext());
        toolbar.setBackgroundColor(ThemeUtils.getToolbarBackgroundColor());
        toolbar.setNavigationIcon(ThemeUtils.getBackButtonDrawable());
        toolbar.setNavigationOnClickListener(view -> VideoQualitySettingsActivity.this.onBackPressed());
        toolbar.setTitle(rvxSettingsLabel);
        int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        toolbar.setTitleMarginStart(margin);
        toolbar.setTitleMarginEnd(margin);
        TextView toolbarTextView = Utils.getChildView(toolbar, view -> view instanceof TextView);
        if (toolbarTextView != null) {
            toolbarTextView.setTextColor(ThemeUtils.getForegroundColor());
        }
        setToolbarLayoutParams(toolbar);
        toolBarParent.addView(toolbar, 0);
    }

    private void setSearchView() {
        SearchView searchView = findViewById(ResourceUtils.getIdIdentifier("search_view"));

        // region compose search hint

        // if the translation is missing the %s, then it
        // will use the default search hint for that language
        String finalSearchHint = String.format(searchLabel, rvxSettingsLabel);

        searchView.setQueryHint(finalSearchHint);

        // endregion

        // region set the font size

        try {
            // 'android.widget.SearchView' has been deprecated quite a long time ago
            // So access the SearchView's EditText via reflection
            Field field = searchView.getClass().getDeclaredField("mSearchSrcTextView");
            field.setAccessible(true);

            // Set the font size
            if (field.get(searchView) instanceof EditText searchEditText) {
                searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            }
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            Logger.printException(() -> "Reflection error accessing mSearchSrcTextView", ex);
        }

        // endregion

        // region SearchView dimensions

        // Get the current layout parameters of the SearchView
        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) searchView.getLayoutParams();

        // Set the margins (in pixels)
        int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics()); // for example, 10dp
        layoutParams.setMargins(margin, layoutParams.topMargin, margin, layoutParams.bottomMargin);

        // Apply the layout parameters to the SearchView
        searchView.setLayoutParams(layoutParams);

        // endregion

        // region SearchView color

        searchView.setBackground(ThemeUtils.getSearchViewShape());

        // endregion

        // Set the listener for query text changes
        searchView.setOnQueryTextListener(onQueryTextListener);

        searchView.setOnQueryTextFocusChangeListener((view, hasFocus) -> {
            Logger.printDebug(() -> "SearchView focus changed: " + hasFocus);
            if (hasFocus) {
                fragment.filterPreferences(""); // Show history when focused
            }
        });

        // Keep a weak reference to the SearchView
        searchViewRef = new WeakReference<>(searchView);
    }

    public void updateToolbarTitle(String title) {
        if (toolbar != null) {
            toolbar.setTitle(title);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        SearchView searchView = searchViewRef.get();
        if (!hasFocus && searchView != null && searchView.getQuery().length() == 0) {
            searchView.clearFocus();
        }
    }
}
