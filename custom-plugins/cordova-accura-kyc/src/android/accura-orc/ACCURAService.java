package accura.kyc.plugin;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.accurascan.facedetection.LivenessCustomization;
import com.accurascan.facedetection.SelfieCameraActivity;
import com.accurascan.facedetection.model.AccuraVerificationResult;
import com.accurascan.facedetection.utils.AccuraLivenessLog;
import com.accurascan.ocr.mrz.model.BarcodeFormat;
import com.accurascan.ocr.mrz.model.ContryModel;
import com.accurascan.ocr.mrz.util.AccuraLog;
import com.androidnetworking.AndroidNetworking;
import com.docrecog.scan.RecogEngine;
import com.google.gson.Gson;
import com.inet.facelock.callback.FaceHelper;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import accura.kyc.app.R;
import okhttp3.OkHttpClient;

public class ACCURAService extends CordovaPlugin {
    CallbackContext livenessCL = null;
    public static CallbackContext faceCL = null;
    public static CallbackContext ocrCL = null;
    public static boolean ocrCLProcess = false;
    boolean livenessWithFace = false;
    public ACCURAService() {
        super();
        Log.e("t", "run");
//        faceHelper = new FaceHelper(cordova.getActivity());
    }
    public static String getSaltString() {
        String SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        StringBuilder salt = new StringBuilder();
        Random rnd = new Random();
        while (salt.length() < 18) { // length of the random string.
            int index = (int) (rnd.nextFloat() * SALTCHARS.length());
            salt.append(SALTCHARS.charAt(index));
        }
        return salt.toString();
    }

    public static String getImageUri(Bitmap bitmap, String name, String path) {
        Log.e("File", path);
        OutputStream fOut = null;
        File file = new File(path, getSaltString() + "_" + name + ".jpg");
        try {
            fOut = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
        try {
            fOut.flush(); // Not really required
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "file://"+file.getAbsolutePath();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("getMetadata")) {
            ocrCL = callbackContext;
            RecogEngine recogEngine = new RecogEngine();
            AccuraLog.enableLogs(false); // make sure to disable logs in release mode
            AccuraLivenessLog.setDEBUG(false);
            recogEngine.setDialog(false); // setDialog(false) To set your custom dialog for license validation
            JSONObject results = new JSONObject();
            RecogEngine.SDKModel sdkModel = recogEngine.initEngine(cordova.getContext());
            if (sdkModel.i >= 0) {
                results.put("isValid", true);
                // if OCR enable then get card list
                if (sdkModel.isOCREnable) {
                    results.put("isOCR", true);
                    List<ContryModel> modelList = recogEngine.getCardList(cordova.getActivity());
                    JSONArray countries = new JSONArray();
                    for (int i = 0; i < modelList.size(); i++) {
                        JSONObject country = new JSONObject();
                        country.put("name", modelList.get(i).getCountry_name());
                        country.put("id", modelList.get(i).getCountry_id());
                        JSONArray cards = new JSONArray();
                        List<ContryModel.CardModel> cardList = modelList.get(i).getCards();
                        for (int j = 0; j < cardList.size(); j++) {
                            JSONObject card = new JSONObject();
                            card.put("name", cardList.get(j).getCard_name());
                            card.put("id", cardList.get(j).getCard_id());
                            card.put("type", cardList.get(j).getCard_type());
                            cards.put(card);
                        }
                        country.put("cards", cards);
                        countries.put(country);
                    }
                    results.put("countries", countries);
                }
                results.put("isOCREnable", sdkModel.isOCREnable);
                results.put("isBarcode", sdkModel.isAllBarcodeEnable);
                if (sdkModel.isAllBarcodeEnable) {
                    List<BarcodeFormat> CODE_NAMES = BarcodeFormat.getList();
                    JSONArray barcodes = new JSONArray();
                    for (int i = 0; i < CODE_NAMES.size(); i++) {
                        JSONObject barcode = new JSONObject();
                        barcode.put("name", CODE_NAMES.get(i).barcodeTitle);
                        barcode.put("type", CODE_NAMES.get(i).formatsType);
                        barcodes.put(barcode);
                    }
                    results.put("barcodes", barcodes);
                }
                results.put("isBankCard", sdkModel.isBankCardEnable);
                results.put("isMRZ", sdkModel.isMRZEnable);

            } else {
                results.put("isValid", false);
            }
            callbackContext.success(results);
            return true;
        }
        if (action.equals("startOcrWithCard")) {
            int country = args.getInt(1);
            int card = args.getInt(2);
            String cardName = args.getString(3);
            int cardType = args.getInt(4);
            Intent myIntent = new Intent(cordova.getActivity(), NavigateActivity.class);
            JSONObject accuraConf = args.getJSONObject(0);
            myIntent = addDefaultConfigs(myIntent, accuraConf);
            myIntent.putExtra("type", "ocr");
            myIntent.putExtra("country_id", country);
            myIntent.putExtra("card_id", card);
            myIntent.putExtra("card_name", cardName);
            myIntent.putExtra("card_type", cardType);
            ocrCL = callbackContext;
            cordova.getActivity().startActivity(myIntent);
            return true;
        }
        if (action.equals("startMRZ")) {
            String type = args.getString(1);
            Intent myIntent = new Intent(cordova.getActivity(), NavigateActivity.class);
            JSONObject accuraConf = args.getJSONObject(0);
            myIntent = addDefaultConfigs(myIntent, accuraConf);
            myIntent.putExtra("type", "mrz");
            myIntent.putExtra("sub-type", type);
            ocrCL = callbackContext;
            cordova.getActivity().startActivity(myIntent);
            return true;
        }
        if (action.equals("startBankCard")) {
            Intent myIntent = new Intent(cordova.getActivity(), NavigateActivity.class);
            JSONObject accuraConf = args.getJSONObject(0);
            myIntent = addDefaultConfigs(myIntent, accuraConf);
            myIntent.putExtra("type", "bankcard");
            ocrCL = callbackContext;
            cordova.getActivity().startActivity(myIntent);
            return true;
        }
        if (action.equals("startBarcode")) {
            String type = args.getString(1);
            Intent myIntent = new Intent(cordova.getActivity(), NavigateActivity.class);
            JSONObject accuraConf = args.getJSONObject(0);
            myIntent = addDefaultConfigs(myIntent, accuraConf);
            myIntent.putExtra("type", "barcode");
            myIntent.putExtra("sub-type", type);
            ocrCL = callbackContext;
            cordova.getActivity().startActivity(myIntent);
            return true;
        }
        if (action.equals("startFaceMatch")) {
            JSONObject config = args.getJSONObject(1);
            faceCL = callbackContext;
            Intent intent = new Intent(cordova.getActivity(), FaceMatchActivity.class);
            JSONObject accuraConf = args.getJSONObject(0);
            intent = addDefaultConfigs(intent, accuraConf);
            intent = addDefaultConfigs(intent, config);
            cordova.getActivity().startActivity(intent);
            return true;
        }
        if (action.equals("startLiveness")) {
            livenessCL = callbackContext;
            Resources res = cordova.getActivity().getResources();
            JSONObject accuraConf = args.getJSONObject(0);
            livenessWithFace = accuraConf.getBoolean("with_face");
            JSONObject config = args.getJSONObject(1);
            LivenessCustomization livenessCustomization = new LivenessCustomization();
            livenessCustomization.backGroundColor = res.getColor(R.color.livenessBackground);
            if (config.has("livenessBackground")) {
                livenessCustomization.backGroundColor = Color.parseColor(config.getString("livenessBackground"));
            }
            livenessCustomization.closeIconColor = res.getColor(R.color.livenessCloseIcon);
            if (config.has("livenessCloseIcon")) {
                livenessCustomization.closeIconColor = Color.parseColor(config.getString("livenessCloseIcon"));
            }
            livenessCustomization.feedbackBackGroundColor = res.getColor(R.color.livenessfeedbackBg);
            if (config.has("livenessfeedbackBg")) {
                livenessCustomization.feedbackBackGroundColor = Color.parseColor(config.getString("livenessfeedbackBg"));
            }
            livenessCustomization.feedbackTextColor = res.getColor(R.color.livenessfeedbackText);
            if (config.has("livenessfeedbackText")) {
                livenessCustomization.feedbackTextColor = Color.parseColor(config.getString("livenessfeedbackText"));
            }
            livenessCustomization.feedbackTextSize = res.getInteger(R.integer.feedbackTextSize);
            if (config.has("livenessfeedbackText")) {
                livenessCustomization.feedbackTextSize = config.getInt("feedbackTextSize");
            }
            livenessCustomization.feedBackframeMessage = res.getString(R.string.feedBackframeMessage);
            if (config.has("feedBackframeMessage")) {
                livenessCustomization.feedBackframeMessage = config.getString("feedBackframeMessage");
            }
            livenessCustomization.feedBackAwayMessage = res.getString(R.string.feedBackAwayMessage);
            if (config.has("feedBackAwayMessage")) {
                livenessCustomization.feedBackAwayMessage = config.getString("feedBackAwayMessage");
            }
            livenessCustomization.feedBackOpenEyesMessage = res.getString(R.string.feedBackOpenEyesMessage);
            if (config.has("feedBackOpenEyesMessage")) {
                livenessCustomization.feedBackOpenEyesMessage = config.getString("feedBackOpenEyesMessage");
            }
            livenessCustomization.feedBackCloserMessage = res.getString(R.string.feedBackCloserMessage);
            if (config.has("feedBackCloserMessage")) {
                livenessCustomization.feedBackCloserMessage = config.getString("feedBackCloserMessage");
            }
            livenessCustomization.feedBackCenterMessage = res.getString(R.string.feedBackCenterMessage);
            if (config.has("feedBackCenterMessage")) {
                livenessCustomization.feedBackCenterMessage = config.getString("feedBackCenterMessage");
            }
            livenessCustomization.feedBackMultipleFaceMessage = res.getString(R.string.feedBackMultipleFaceMessage);
            if (config.has("feedBackMultipleFaceMessage")) {
                livenessCustomization.feedBackMultipleFaceMessage = config.getString("feedBackMultipleFaceMessage");
            }
            livenessCustomization.feedBackHeadStraightMessage = res.getString(R.string.feedBackHeadStraightMessage);
            if (config.has("feedBackHeadStraightMessage")) {
                livenessCustomization.feedBackHeadStraightMessage = config.getString("feedBackHeadStraightMessage");
            }
            livenessCustomization.feedBackBlurFaceMessage = res.getString(R.string.feedBackBlurFaceMessage);
            if (config.has("feedBackBlurFaceMessage")) {
                livenessCustomization.feedBackBlurFaceMessage = config.getString("feedBackBlurFaceMessage");
            }
            livenessCustomization.feedBackGlareFaceMessage = res.getString(R.string.feedBackGlareFaceMessage);
            if (config.has("feedBackGlareFaceMessage")) {
                livenessCustomization.feedBackGlareFaceMessage = config.getString("feedBackGlareFaceMessage");
            }
            livenessCustomization.feedBackVideoRecordingMessage = res.getString(R.string.feedBackVideoRecordingMessage);
            if (config.has("feedBackVideoRecordingMessage")) {
                livenessCustomization.feedBackVideoRecordingMessage = config.getString("feedBackVideoRecordingMessage");
            }
            livenessCustomization.setBlurPercentage(res.getInteger(R.integer.setBlurPercentage));
            if (config.has("setBlurPercentage")) {
                livenessCustomization.setBlurPercentage(config.getInt("setBlurPercentage"));
            }
            int minGlare = res.getInteger(R.integer.setGlarePercentage_0);
            int maxGlare = res.getInteger(R.integer.setGlarePercentage_1);
            if (config.has("setGlarePercentage_0")) {
                minGlare = config.getInt("setGlarePercentage_0");
            }
            if (config.has("setGlarePercentage_1")) {
                minGlare = config.getInt("setGlarePercentage_1");
            }
            livenessCustomization.setGlarePercentage(minGlare, maxGlare);
            livenessCustomization.isSaveImage = res.getBoolean(R.bool.isSaveImage);
            if (config.has("isSaveImage")) {
                livenessCustomization.isSaveImage = config.getBoolean("isSaveImage");
            }
            livenessCustomization.isRecordVideo = res.getBoolean(R.bool.isRecordVideo);
            if (config.has("isRecordVideo")) {
                livenessCustomization.isRecordVideo = config.getBoolean("isRecordVideo");
            }
            // video length in seconds
            livenessCustomization.videoLengthInSecond = res.getInteger(R.integer.videoLengthInSecond);
            if (config.has("videoLengthInSecond")) {
                livenessCustomization.videoLengthInSecond = config.getInt("videoLengthInSecond");
            }
            livenessCustomization.recordingTimerTextColor = res.getColor(R.color.livenessRecordingText);
            if (config.has("livenessRecordingTextColor")) {
                livenessCustomization.recordingTimerTextColor = Color.parseColor(config.getString("livenessRecordingTextColor"));
            }
            livenessCustomization.recordingTimerTextSize = res.getInteger(R.integer.recordingTimerTextSize);
            if (config.has("recordingTimerTextSize")) {
                livenessCustomization.recordingTimerTextSize = config.getInt("recordingTimerTextSize");
            }
            livenessCustomization.recordingMessage = res.getString(R.string.recordingMessage);
            if (config.has("recordingMessage")) {
                livenessCustomization.recordingMessage = config.getString("recordingMessage");
            }
            livenessCustomization.recordingMessageTextColor = res.getColor(R.color.livenessRecordingText);
            if (config.has("livenessRecordingTextColor")) {
                livenessCustomization.recordingMessageTextColor = Color.parseColor(config.getString("livenessRecordingTextColor"));
            }
            livenessCustomization.recordingMessageTextSize = res.getInteger(R.integer.recordingMessageTextSize);
            if (config.has("recordingMessageTextSize")) {
                livenessCustomization.recordingMessageTextSize = config.getInt("recordingMessageTextSize");
            }
            livenessCustomization.enableFaceDetect = res.getBoolean(R.bool.enableFaceDetect);
            if (config.has("enableFaceDetect")) {
                livenessCustomization.enableFaceDetect = config.getBoolean("enableFaceDetect");
            }
            livenessCustomization.enableFaceMatch = res.getBoolean(R.bool.enableFaceMatch);
            if (config.has("enableFaceMatch")) {
                livenessCustomization.enableFaceMatch = config.getBoolean("enableFaceMatch");
            }
            livenessCustomization.fmScoreThreshold = res.getInteger(R.integer.fmScoreThreshold);
            if (config.has("fmScoreThreshold")) {
                livenessCustomization.fmScoreThreshold = config.getInt("fmScoreThreshold");
            }
            livenessCustomization.feedbackFMFailed = res.getString(R.string.feedbackFMFailed);
            if (config.has("feedbackFMFailed")) {
                livenessCustomization.feedbackFMFailed = config.getString("feedbackFMFailed");
            }
            OkHttpClient okHttpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient();
            AndroidNetworking.initialize(cordova.getContext(), okHttpClient);
            String liveUrl = res.getString(R.string.liveness_url);
            if (config.has("liveness_url")) {
                liveUrl = config.getString("liveness_url");
            }
            Intent intent = SelfieCameraActivity.getLivenessCameraIntent(cordova.getActivity(), livenessCustomization, liveUrl);
            cordova.setActivityResultCallback(this);
            cordova.getActivity().startActivityForResult(intent, 201);
            return true;
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.e("onActivityResult", requestCode + "");
        if (requestCode == 201 && data != null) {
            JSONObject results = new JSONObject();
            try {
                results.put("status", false);
                results.put("type", "liveness");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            AccuraVerificationResult result = data.getParcelableExtra("Accura.liveness");
            if (result != null) {
                if (result.getStatus().equals("1")) {
                    livenessCL.success(handleVerificationSuccessResult(result, results));
                } else {
                    livenessCL.error(results);
                    if (result.getVideoPath() != null) {
                        Toast.makeText(cordova.getContext(), "Video Path : " + result.getVideoPath() + "\n" + result.getErrorMessage(), Toast.LENGTH_LONG).show();
                    } else
                        Toast.makeText(cordova.getContext(), result.getErrorMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public JSONObject handleVerificationSuccessResult(final AccuraVerificationResult result, JSONObject caResults) {
        if (result != null) {
            if (result.getLivenessResult() != null) {
                if (result.getLivenessResult().getLivenessStatus()) {
                    try {
                        caResults.put("status", true);
                        caResults.put("with_face", livenessWithFace);
                        caResults.put("score", result.getLivenessResult().getLivenessScore() * 100);
                        if (result.getFaceBiometrics() != null) {
                            caResults.put("detect", getImageUri(result.getFaceBiometrics(), "live_detect", this.cordova.getActivity().getFilesDir().getAbsolutePath()));
                        }
                        if (result.getImagePath() != null) {
                            caResults.put("image_uri", result.getImagePath());
                        }
                        if (result.getVideoPath() != null) {
                            caResults.put("video_uri", result.getVideoPath());
                        }
                    } catch (JSONException ignored) {
                    }
                }
            }
        }
        Log.e("face", caResults.toString());

        return caResults;

    }

    public Intent addDefaultConfigs(Intent intent, JSONObject config) {
        Iterator<String> iter = config.keys();
        while (iter.hasNext()) {
            String key = iter.next();
            try {
                if (config.get(key) instanceof String) {
                    intent.putExtra(key, config.getString(key));
                }
                if (config.get(key) instanceof Boolean) {
                    intent.putExtra(key, config.getBoolean(key));
                }
                if (config.get(key) instanceof Integer) {
                    intent.putExtra(key, config.getInt(key));
                }
                if (config.get(key) instanceof Double) {
                    intent.putExtra(key, config.getDouble(key));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }
        return intent;
    }
}