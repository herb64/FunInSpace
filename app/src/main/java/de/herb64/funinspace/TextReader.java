package de.herb64.funinspace;

import android.os.Build;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.Locale;

import de.herb64.funinspace.helpers.utils;

/**
 * Created by herbert on 20.01.18.
 * Headless retained fragment class used to read out text using tts system in order to survive
 * configuration events (phone rotation)
 *
 * TODO
 * see https://stackoverflow.com/questions/33145134/leaked-serviceconnection-android-speech-tts-texttospeechconnection
 *
 * how to prevent stop at rotation?
 * https://stackoverflow.com/questions/15511513/how-to-prevent-texttospeech-from-stopping-during-rotation?rq=1
 */

public class TextReader extends Fragment implements TextToSpeech.OnInitListener {

    private TextToSpeech tts = null;

    public static final String TAG_READ_FRAGMENT = "text_reader_fragment";

    /**
     * onCreate()
     * @param savedInstanceState instance state
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        Log.i("HFCM", "TextReader - onCreate()");
        if (tts == null) {
            tts = new TextToSpeech(getActivity().getApplicationContext(), this);
            // Docu: on rotation of phone:
            // MainActivity has leaked ServiceConnection android.speech.tts.TextToSpeech$Connection
            // solution for this: use getApplicationContext() is needed!!
        }
    }

    /**
     *
     */
    @Override
    public void onDestroy() {
        if (tts != null) {
            tts.stop();
            //tts.shutdown();
            // shutdown: fails
            //java.lang.IllegalArgumentException: Service not registered: android.speech.tts.TextToSpeech$Connection@f9f375a
            // when running speak via cab, then rotating, rotating back and using back button
            // https://developer.android.com/reference/android/speech/tts/TextToSpeechService.html
        }
        super.onDestroy();
    }

    /**
     * Stop the instance
     */
    public void stop() {
        if (tts != null) {
            tts.stop();
        }
    }

    public  void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    /**
     * Read out title and explanation
     * @param title the title string
     * @param explanation the explanation string
     * @return
     */
    public boolean read(String title, String explanation) {
        if (tts != null && !tts.isSpeaking()) {
            Log.i("HFCM", "Read for '" + title + "'");
            // deprecations
            // https://stackoverflow.com/questions/27968146/texttospeech-with-api-21
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts.speak("Title",
                        TextToSpeech.QUEUE_ADD,
                        null, null);
            } else {
                tts.speak("Title",
                        TextToSpeech.QUEUE_ADD,
                        null);
            }
            tts.playSilence(500L,
                    TextToSpeech.QUEUE_ADD,
                    null);
            tts.speak(title,
                    TextToSpeech.QUEUE_ADD,
                    null);
            tts.playSilence(500L,
                    TextToSpeech.QUEUE_ADD,
                    null);
            tts.speak("Explanation",
                    TextToSpeech.QUEUE_ADD,
                    null);
            tts.playSilence(500L,
                    TextToSpeech.QUEUE_ADD,
                    null);
            tts.speak(explanation,
                    TextToSpeech.QUEUE_ADD,
                    null);
            return true;
        } else if (tts != null) {
            tts.stop();
            return false;
        }
        return false;
    }

    /**
     * Check status of reader
     * @return true if reading is active
     */
    public boolean isReading() {
        return tts != null && tts.isSpeaking();
    }


    /**
     * Implementation for onInitListener of TextToSpeech. This is called, if tts init is done.
     * @param status tts status
     */
    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.ERROR) {
            if (tts.setLanguage(Locale.ENGLISH) == TextToSpeech.LANG_AVAILABLE) {
            } else {
                utils.logAppend(this.getContext(),
                        MainActivity.DEBUG_LOG,
                        "TTS init: Locale '" + Locale.ENGLISH.toString() + "' missing.");
            }

        } else {
            utils.logAppend(this.getContext(),
                    MainActivity.DEBUG_LOG,
                    "TTS init failed...");
        }
    }
}
