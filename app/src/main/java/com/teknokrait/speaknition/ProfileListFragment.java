package com.teknokrait.speaknition;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.microsoft.cognitive.speakerrecognition.SpeakerIdentificationClient;
import com.microsoft.cognitive.speakerrecognition.contract.identification.CreateProfileResponse;
import com.microsoft.cognitive.speakerrecognition.contract.identification.Profile;

import org.jdeferred.android.AndroidDeferredManager;
import org.jdeferred.android.AndroidExecutionScope;
import org.jdeferred.android.AndroidFailCallback;
import org.jdeferred.android.DeferredAsyncTask;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ProfileListFragment extends Fragment {

    private static final String TAG = ProfileListFragment.class.getSimpleName();

    private static final String MENU_TITLE_ADD = "Add";

    private final SpeakerIdentificationClient client = SpeakerIdentificationFactory.getSpeakerIdentificationClient();

    private RecyclerView recyclerView;

    private final List<Profile> viewModels = new ArrayList<>();

    public static ProfileListFragment newInstance() {
        return new ProfileListFragment();
    }

    public static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    public static final int MY_PERMISSIONS_REQUEST_STORAGE = 2;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_list, container, false);

        setHasOptionsMenu(true);

        recyclerView = (RecyclerView) view.findViewById(R.id.list);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermission();
        }

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getActivity().setTitle(getString(R.string.title_profiles));

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);
        recyclerView.addItemDecoration(new DividerItemDecoration(getContext(), DividerItemDecoration.HORIZONTAL));

        recyclerView.setAdapter(new UserProfileAdapter());

        fetchProfiles();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        MenuItem addItem = menu.add(MENU_TITLE_ADD);
        addItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (MENU_TITLE_ADD.equals(item.getTitle())) {
            createProfile();
        }
        return false;
    }

    public boolean checkPermission() {
        if (ContextCompat.checkSelfPermission(getActivity(),
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),
                    Manifest.permission.RECORD_AUDIO)) {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                //  TODO: Prompt with explanation!

                //Prompt the user once explanation has been shown
                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
                permissionStorage();
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
                permissionStorage();
            }
            return false;
        }
        else {
            return true;
        }
    }

    private void permissionStorage() {
        if (ContextCompat.checkSelfPermission(getActivity(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                //  TODO: Prompt with explanation!

                //Prompt the user once explanation has been shown
                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_STORAGE);

            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_STORAGE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_RECORD_AUDIO: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay!
                    if (ActivityCompat.checkSelfPermission(getActivity(),
                            Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {

                    }
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(getActivity(), "permission denied", Toast.LENGTH_LONG).show();
                }
                return;
            }
            case MY_PERMISSIONS_REQUEST_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay!
                    if (ActivityCompat.checkSelfPermission(getActivity(),
                            Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {

                    }
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(getActivity(), "permission denied", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    private void fetchProfiles() {
        // jdeferred
        // See https://github.com/jdeferred/jdeferred
        new AndroidDeferredManager().when(new DeferredAsyncTask<Void, Object, List<Profile>>() {
            @Override
            protected List<Profile> doInBackgroundSafe(Void... voids) throws Exception {
                List<Profile> profiles = client.getProfiles();
                // Sort by createdDateTime.
                Collections.sort(profiles, (p1, p2) -> p2.createdDateTime.compareTo(p1.createdDateTime));
                return profiles;
            }
        }).done(profiles -> {
            for (Profile profile : profiles) {
                Log.d(TAG, "identificationProfileId: " + profile.identificationProfileId);
                Log.d(TAG, "enrollmentSpeechTime: " + profile.enrollmentSpeechTime);
                Log.d(TAG, "remainingEnrollmentSpeechTime: " + profile.remainingEnrollmentSpeechTime);
                Log.d(TAG, "locale: " + profile.locale);
                Log.d(TAG, "createdDateTime: " + profile.createdDateTime);
                Log.d(TAG, "lastActionDateTime: " + profile.lastActionDateTime);
                Log.d(TAG, "enrollmentStatus: " + profile.enrollmentStatus);
            }

            viewModels.clear();
            viewModels.addAll(profiles);

            recyclerView.getAdapter().notifyDataSetChanged();
        }).fail(new AndroidFailCallback<Throwable>() {

            @Override
            public void onFail(Throwable throwable) {
                Log.e(TAG, "Failed to fetch user profiles.", throwable);
            }

            @Override
            public AndroidExecutionScope getExecutionScope() {
                return null;
            }
        });
    }

    private void createProfile() {
        AndroidDeferredManager manager = new AndroidDeferredManager();
        manager.when(new DeferredAsyncTask<Void, Object, CreateProfileResponse>() {

            @Override
            protected CreateProfileResponse doInBackgroundSafe(Void... voids) throws Exception {
                // Support en-us or zh-CN.
                return client.createProfile("en-us");
            }
        }).done(response -> {
            Log.d(TAG, "Succeeded to create profile. IdentificationProfileId:" + response.identificationProfileId);
            fetchProfiles();
        }).fail(new AndroidFailCallback<Throwable>() {

            @Override
            public void onFail(Throwable throwable) {
                Log.e(TAG, "Failed to fetch user profiles.", throwable);
                Toast.makeText(getContext(), "Failed.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public AndroidExecutionScope getExecutionScope() {
                return null;
            }
        });
    }

    // Adapter
    private class UserProfileAdapter extends RecyclerView.Adapter<UserProfileAdapter.UserProfileViewItemHolder> {

        @Override
        public UserProfileViewItemHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile, parent, false);
            return new UserProfileViewItemHolder(itemView);
        }

        @Override
        public void onBindViewHolder(UserProfileViewItemHolder holder, int position) {
            Profile profile = viewModels.get(position);
            holder.showItem(profile);
        }

        @Override
        public int getItemCount() {
            return viewModels.size();
        }

        class UserProfileViewItemHolder extends RecyclerView.ViewHolder {

            private LinearLayout itemLayout;

            private TextView identificationIdTextView;

            private TextView createdDateTimeTextView;

            private TextView enrollmentStatusTextView;

            UserProfileViewItemHolder(View itemView) {
                super(itemView);
                itemLayout = (LinearLayout) itemView.findViewById(R.id.layoutItem);
                identificationIdTextView = (TextView) itemView.findViewById(R.id.textViewIdentificationId);
                createdDateTimeTextView = (TextView) itemView.findViewById(R.id.textViewCreatedDateTime);
                enrollmentStatusTextView = (TextView) itemView.findViewById(R.id.textViewEnrollmentStatus);
            }

            private void showItem(Profile profile) {
                identificationIdTextView.setText(profile.identificationProfileId.toString());

                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());
                String createdDateTime = simpleDateFormat.format(profile.createdDateTime);
                createdDateTimeTextView.setText(createdDateTime);

                enrollmentStatusTextView.setText(profile.enrollmentStatus.toString());

                itemLayout.setOnClickListener(v -> {
                    MainActivity mainActivity = (MainActivity) getActivity();
                    mainActivity.showAudioRegisterFragment(profile.identificationProfileId.toString());
                });
            }
        }
    }
}
