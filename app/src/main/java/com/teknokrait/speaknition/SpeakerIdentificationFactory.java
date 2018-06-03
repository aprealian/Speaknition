package com.teknokrait.speaknition;

import com.microsoft.cognitive.speakerrecognition.SpeakerIdentificationClient;
import com.microsoft.cognitive.speakerrecognition.SpeakerIdentificationRestClient;

final class SpeakerIdentificationFactory {

    // TODO: You must set your subscription key.
    private static final String SUBSCRIPTION_KEY = "efd13991f15f486dac04dcfbcab81993";

    private static final SpeakerIdentificationClient INSTANCE = new SpeakerIdentificationRestClient(SUBSCRIPTION_KEY);

    static SpeakerIdentificationClient getSpeakerIdentificationClient() {
        return INSTANCE;
    }
}
