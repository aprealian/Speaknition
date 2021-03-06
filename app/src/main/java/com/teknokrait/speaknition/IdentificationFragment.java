package com.teknokrait.speaknition;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.ybq.android.spinkit.SpinKitView;
import com.github.ybq.android.spinkit.style.DoubleBounce;
import com.github.ybq.android.spinkit.style.Wave;
import com.microsoft.cognitive.speakerrecognition.SpeakerIdentificationClient;
import com.microsoft.cognitive.speakerrecognition.contract.EnrollmentStatus;
import com.microsoft.cognitive.speakerrecognition.contract.identification.Identification;
import com.microsoft.cognitive.speakerrecognition.contract.identification.IdentificationOperation;
import com.microsoft.cognitive.speakerrecognition.contract.identification.OperationLocation;
import com.microsoft.cognitive.speakerrecognition.contract.identification.Profile;
import com.microsoft.cognitive.speakerrecognition.contract.identification.Status;

import org.jdeferred.android.AndroidDeferredManager;
import org.jdeferred.android.AndroidExecutionScope;
import org.jdeferred.android.AndroidFailCallback;
import org.jdeferred.android.DeferredAsyncTask;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class IdentificationFragment extends Fragment {

    private static final String TAG = IdentificationFragment.class.getSimpleName();

    private static final int SAMPLING_RATE = 16000;

    private final SpeakerIdentificationClient client = SpeakerIdentificationFactory.getSpeakerIdentificationClient();

    private final int bufferSize = AudioRecord.getMinBufferSize(SAMPLING_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

    private final AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

    private boolean isRecording;

    private Button identificationButton;

    private LinearLayout microphoneLinearLayout, resultLinearLayout;

    private Button checkResultButton;

    private TextView textView, resultTextView, youAreTextView;

    private SpinKitView spinKitView;

    private OperationLocation operationLocation;

    public static IdentificationFragment newInstance() {
        return new IdentificationFragment();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_identification, container, false);

        textView = (TextView) view.findViewById(R.id.textView);
        youAreTextView = (TextView) view.findViewById(R.id.tv_you_are);
        resultTextView = (TextView) view.findViewById(R.id.tv_result);
        resultLinearLayout = (LinearLayout) view.findViewById(R.id.ll_result);

        spinKitView = (SpinKitView) view.findViewById(R.id.spin_kit);
        Wave doubleBounce = new Wave();
        spinKitView.setIndeterminateDrawable(doubleBounce);

        identificationButton = (Button) view.findViewById(R.id.buttonIdentification);
        identificationButton.setOnClickListener(v -> startIdentification());

        microphoneLinearLayout = (LinearLayout) view.findViewById(R.id.microphone_linearLayout);
        microphoneLinearLayout.setOnClickListener(v -> startIdentification());

        checkResultButton = (Button) view.findViewById(R.id.buttonCheckResult);
        checkResultButton.setOnClickListener(v -> checkResult());

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getActivity().setTitle(getString(R.string.title_identification));
    }

    private void startIdentification() {
        textView.setText(R.string.textView_identification_description);
        spinKitView.setVisibility(View.VISIBLE);
        identificationButton.setEnabled(false);
        microphoneLinearLayout.setVisibility(View.GONE);
        youAreTextView.setVisibility(View.GONE);
        resultTextView.setVisibility(View.GONE);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //User has previously accepted this permission
            if (ActivityCompat.checkSelfPermission(getActivity(),
                    Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                audioRecord.startRecording();
            }
        } else {
            //Not in api-23, no need to prompt
            audioRecord.startRecording();
        }
        isRecording = true;

        new Thread(() -> {
            // First, write temp file.
            String filePath = FileHelper.getTempFilename();
            FileOutputStream outputStream = null;
            try {
                outputStream = new FileOutputStream(filePath);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Failed to find audio file.", e);
                Toast.makeText(getContext(), "Failed.", Toast.LENGTH_SHORT).show();
                return;
            }

            byte buf[] = new byte[bufferSize];
            while (isRecording) {
                audioRecord.read(buf, 0, buf.length);

                Log.v(TAG, "read " + buf.length + " bytes");

                try {
                    outputStream.write(buf);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to write audio data.", e);
                    Toast.makeText(getContext(), "Failed.", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // After recording, copy from temp file to WAV file.
            if (!isRecording) {
                // Close stream.
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to close stream.", e);
                    Toast.makeText(getContext(), "Failed.", Toast.LENGTH_SHORT).show();
                    return;
                }

                FileHelper.copyWaveFile(bufferSize, "xxx");
                FileHelper.deleteTempFile();
                
                doIdentification();
            }
        }).start();

        // Stop recording after 5 seconds.
        executor.schedule(new TimerTask() {

            @Override
            public void run() {
                // Stop recording.
                isRecording = false;
                spinKitView.setVisibility(View.GONE);
                textView.setVisibility(View.GONE);
                checkResultButton.setVisibility(View.VISIBLE);

                Log.v(TAG, "stop");
                audioRecord.stop();

                identificationButton.setEnabled(true);
            }
        }, 5, TimeUnit.SECONDS);
    }

    private void doIdentification() {
        // Fetch enrolled identificationProfileIds.
        AndroidDeferredManager manager = new AndroidDeferredManager();
        manager.when(new DeferredAsyncTask<Void, Object, List<Profile>>() {
            @Override
            protected List<Profile> doInBackgroundSafe(Void... voids) throws Exception {
                return client.getProfiles();
            }
        }).done(profiles -> {
            List<UUID> identificationProfileIds = new ArrayList<>(profiles.size());
            for (Profile profile : profiles) {
                Log.d(TAG, "identificationProfileId: " + profile.identificationProfileId);
                Log.d(TAG, "enrollmentSpeechTime: " + profile.enrollmentSpeechTime);
                Log.d(TAG, "remainingEnrollmentSpeechTime: " + profile.remainingEnrollmentSpeechTime);
                Log.d(TAG, "locale: " + profile.locale);
                Log.d(TAG, "createdDateTime: " + profile.createdDateTime);
                Log.d(TAG, "lastActionDateTime: " + profile.lastActionDateTime);
                Log.d(TAG, "enrollmentStatus: " + profile.enrollmentStatus);

                if (EnrollmentStatus.ENROLLED == profile.enrollmentStatus) {
                    identificationProfileIds.add(profile.identificationProfileId);
                }
            }

            if (0 == identificationProfileIds.size()) {
                Toast.makeText(getContext(), "Not enrolled profiles.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (10 < identificationProfileIds.size()) {
                Log.e(TAG, "A maximum of 10 identificationProfileIds can be included in the request parameter. Please remove profiles.");
                Toast.makeText(getContext(), "The identificationProfileIds of the request parameter exceeds 10.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Identification.
            identification(identificationProfileIds);
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

    private void identification(List<UUID> identificationProfileIds) {
        String path = FileHelper.getFilename("xxx");
        AndroidDeferredManager manager = new AndroidDeferredManager();
        manager.when(new DeferredAsyncTask<Void, Object, OperationLocation>() {

            @Override
            protected OperationLocation doInBackgroundSafe(Void... voids) throws Exception {
                InputStream inputStream = new FileInputStream(path);
                return client.identify(inputStream, identificationProfileIds, true);
            }
        }).done(location -> {
            // Recognition processing does not end immediately, so keep operation location here.
            Log.d(TAG, "OperationLocation: " + location.Url);
            operationLocation = location;
            //checkResultButton.setEnabled(true);
            textView.setVisibility(View.GONE);
            //checkResultButton.setVisibility(View.VISIBLE);
            //checkResultButton.setEnabled(true);

            checkResult();

        }).fail(new AndroidFailCallback<Throwable>() {

            @Override
            public void onFail(Throwable e) {
                Log.e(TAG, "Failed to identify.", e);
                Toast.makeText(getContext(), "Failed.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public AndroidExecutionScope getExecutionScope() {
                return null;
            }
        });
    }

    private void checkResult() {
        AndroidDeferredManager manager = new AndroidDeferredManager();
        manager.when(new DeferredAsyncTask<Void, Object, IdentificationOperation>() {

            @Override
            protected IdentificationOperation doInBackgroundSafe(Void... voids) throws Exception {
                return client.checkIdentificationStatus(operationLocation);
            }
        }).done(operation -> {
            Log.d(TAG, "Status: " + operation.status);

            if (Status.SUCCEEDED == operation.status) {
                Identification identification = operation.processingResult;

                //result
                if (identification != null && identification.identifiedProfileId != null && TextUtils.isEmpty(identification.identifiedProfileId.toString())){
                    Toast.makeText(getActivity(), "not null", Toast.LENGTH_SHORT).show();
//                    identificationButton.setText(identification.identifiedProfileId.toString());
//                    youAreTextView.setText("You are "+identification.confidence.toString()+" :");
                    resultTextView.setText(identification.identifiedProfileId.toString());
                    resultTextView.setVisibility(View.VISIBLE);
                    youAreTextView.setVisibility(View.VISIBLE);
                    resultLinearLayout.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(getActivity(), "null", Toast.LENGTH_SHORT).show();
                    youAreTextView.setText("Please, try again");
                    youAreTextView.setVisibility(View.VISIBLE);
                }

                Log.d(TAG, "identifiedProfileId: " + identification.identifiedProfileId);
                Log.d(TAG, "confidence: " + identification.confidence);
                Toast.makeText(getContext(), "You are " + identification.identifiedProfileId, Toast.LENGTH_SHORT).show();

            } else {
                Log.d(TAG, "Not enrolled yet. Message: " + operation.message);
                microphoneLinearLayout.setVisibility(View.VISIBLE);
                youAreTextView.setText("Please, try again");
                youAreTextView.setVisibility(View.VISIBLE);
                Toast.makeText(getContext(), operation.message, Toast.LENGTH_SHORT).show();
            }

            spinKitView.setVisibility(View.GONE);
            microphoneLinearLayout.setVisibility(View.VISIBLE);


        }).fail(new AndroidFailCallback<Throwable>() {

            @Override
            public void onFail(Throwable e) {
                Log.e(TAG, "Failed.", e);
                Toast.makeText(getContext(), "Failed.", Toast.LENGTH_SHORT).show();
                spinKitView.setVisibility(View.GONE);
                microphoneLinearLayout.setVisibility(View.VISIBLE);
                youAreTextView.setText("Please, try again");
                youAreTextView.setVisibility(View.VISIBLE);
            }

            @Override
            public AndroidExecutionScope getExecutionScope() {
                return null;
            }
        });
    }
}
