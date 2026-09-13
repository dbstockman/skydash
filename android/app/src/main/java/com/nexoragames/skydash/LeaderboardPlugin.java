package com.nexoragames.skydash;

import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.games.PlayGames;

@CapacitorPlugin(name = "Leaderboard")
public class LeaderboardPlugin extends Plugin {

    private static final String TAG = "SkyDashGPGS";
    private static final String LEADERBOARD_NAME = "SkyDash High Scores";

    @PluginMethod
    public void submitScore(PluginCall call) {
        long score = call.getLong("score", 0L);

        PlayGames.getGamesSignInClient(getActivity())
                .isAuthenticated()
                .addOnCompleteListener(authTask -> {
                    if (authTask.isSuccessful()
                            && authTask.getResult() != null
                            && authTask.getResult().isAuthenticated()) {
                        submitToSkyDashLeaderboard(call, score);
                        return;
                    }

                    PlayGames.getGamesSignInClient(getActivity())
                            .signIn()
                            .addOnCompleteListener(signInTask -> {
                                if (signInTask.isSuccessful()
                                        && signInTask.getResult() != null
                                        && signInTask.getResult().isAuthenticated()) {
                                    submitToSkyDashLeaderboard(call, score);
                                } else {
                                    Exception error = signInTask.getException();
                                    logFailure("SIGN IN FAILED", error);
                                    call.reject("Google Play Games sign-in failed", error);
                                }
                            });
                });
    }

    private void submitToSkyDashLeaderboard(PluginCall call, long score) {
        findLeaderboardId(new LeaderboardIdCallback() {
            @Override
            public void onSuccess(String leaderboardId) {
                PlayGames.getLeaderboardsClient(getActivity())
                        .submitScoreImmediate(leaderboardId, score)
                        .addOnSuccessListener(result -> {
                            Log.e(TAG, "SCORE SUBMIT SUCCESS | score=" + score);

                            JSObject response = new JSObject();
                            response.put("submitted", true);
                            response.put("score", score);
                            response.put("leaderboardId", leaderboardId);
                            call.resolve(response);
                        })
                        .addOnFailureListener(error -> {
                            logFailure("SCORE SUBMIT FAILED", error);
                            call.reject("Google Play Games score submission failed: " + error.getMessage(), error);
                        });
            }

            @Override
            public void onFailure(Exception error) {
                logFailure("LEADERBOARD LOOKUP FAILED", error);
                call.reject("SkyDash leaderboard not found", error);
            }
        });
    }

    @PluginMethod
    public void showLeaderboard(PluginCall call) {
        findLeaderboardId(new LeaderboardIdCallback() {
            @Override
            public void onSuccess(String leaderboardId) {
                PlayGames.getLeaderboardsClient(getActivity())
                        .getLeaderboardIntent(leaderboardId)
                        .addOnSuccessListener(intent -> {
                            getActivity().startActivityForResult(intent, 9001);
                            call.resolve();
                        })
                        .addOnFailureListener(error -> {
                            logFailure("OPEN LEADERBOARD FAILED", error);
                            call.reject("Failed to open Google Play leaderboard", error);
                        });
            }

            @Override
            public void onFailure(Exception error) {
                logFailure("LEADERBOARD LOOKUP FAILED", error);
                call.reject("SkyDash leaderboard not found", error);
            }
        });
    }

    private void findLeaderboardId(LeaderboardIdCallback callback) {
        PlayGames.getLeaderboardsClient(getActivity())
                .loadLeaderboardMetadata(true)
                .addOnSuccessListener(result -> {
                    String leaderboardId = null;

                    try {
                        int count = result.get().getCount();
                        for (int i = 0; i < count; i++) {
                            String name = result.get().get(i).getDisplayName();
                            if (LEADERBOARD_NAME.equals(name)) {
                                leaderboardId = result.get().get(i).getLeaderboardId();
                                break;
                            }
                        }
                    } finally {
                        result.get().release();
                    }

                    if (leaderboardId != null) {
                        callback.onSuccess(leaderboardId);
                    } else {
                        callback.onFailure(new IllegalStateException("Leaderboard not found: " + LEADERBOARD_NAME));
                    }
                })
                .addOnFailureListener(callback::onFailure);
    }

    private void logFailure(String prefix, Exception error) {
        if (error instanceof ApiException) {
            ApiException apiException = (ApiException) error;
            Log.e(TAG,
                    prefix
                            + " | statusCode=" + apiException.getStatusCode()
                            + " | message=" + apiException.getMessage(),
                    error);
        } else {
            Log.e(TAG,
                    prefix + " | message=" + (error == null ? "null" : error.getMessage()),
                    error);
        }
    }

    private interface LeaderboardIdCallback {
        void onSuccess(String leaderboardId);
        void onFailure(Exception error);
    }
}
