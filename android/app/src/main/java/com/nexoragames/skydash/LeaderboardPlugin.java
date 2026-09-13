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
    private static final String BROKEN_TEST_ID = "Cgk1Mr5rcEQEAIQAw";

    @PluginMethod
    public void submitScore(PluginCall call) {
        long score = call.getLong("score", 0L);
        Log.e(TAG, "REAL SCORE SUBMIT START | score=" + score);

        PlayGames.getGamesSignInClient(getActivity()).isAuthenticated().addOnCompleteListener(auth -> {
            if (auth.isSuccessful() && auth.getResult() != null && auth.getResult().isAuthenticated()) {
                findWorkingLeaderboardAndSubmit(call, score);
            } else {
                PlayGames.getGamesSignInClient(getActivity()).signIn().addOnCompleteListener(signIn -> {
                    if (signIn.isSuccessful() && signIn.getResult() != null && signIn.getResult().isAuthenticated()) {
                        findWorkingLeaderboardAndSubmit(call, score);
                    } else {
                        Exception error = signIn.getException();
                        logFailure("SIGN IN FAILED", error);
                        call.reject("Google Play Games sign-in failed", error);
                    }
                });
            }
        });
    }

    private void findWorkingLeaderboardAndSubmit(PluginCall call, long score) {
        PlayGames.getLeaderboardsClient(getActivity()).loadLeaderboardMetadata(true)
                .addOnSuccessListener(result -> {
                    String workingId = null;
                    String workingName = null;
                    try {
                        int count = result.get().getCount();
                        for (int i = 0; i < count; i++) {
                            String id = result.get().get(i).getLeaderboardId();
                            String name = result.get().get(i).getDisplayName();
                            if (!BROKEN_TEST_ID.equals(id) && workingId == null) {
                                workingId = id;
                                workingName = name;
                            }
                        }
                    } finally {
                        result.get().release();
                    }

                    if (workingId == null) {
                        call.reject("Working leaderboard not found");
                        return;
                    }

                    Log.e(TAG, "USING WORKING LEADERBOARD | id=" + workingId + " | name=" + workingName + " | score=" + score);
                    String finalId = workingId;
                    PlayGames.getLeaderboardsClient(getActivity()).submitScoreImmediate(finalId, score)
                            .addOnSuccessListener(scoreResult -> {
                                Log.e(TAG, "REAL SCORE SUBMIT SUCCESS | id=" + finalId + " | score=" + score);
                                JSObject response = new JSObject();
                                response.put("submitted", true);
                                response.put("score", score);
                                response.put("leaderboardId", finalId);
                                call.resolve(response);
                            })
                            .addOnFailureListener(error -> {
                                logFailure("REAL SCORE SUBMIT FAILED", error);
                                call.reject("Google Play Games score submission failed: " + error.getMessage(), error);
                            });
                })
                .addOnFailureListener(error -> {
                    logFailure("METADATA FAILED", error);
                    call.reject("Leaderboard lookup failed: " + error.getMessage(), error);
                });
    }

    private void logFailure(String prefix, Exception error) {
        if (error instanceof ApiException) {
            ApiException e = (ApiException) error;
            Log.e(TAG, prefix + " | statusCode=" + e.getStatusCode() + " | message=" + e.getMessage(), error);
        } else {
            Log.e(TAG, prefix + " | message=" + (error == null ? "null" : error.getMessage()), error);
        }
    }

    @PluginMethod
    public void showLeaderboard(PluginCall call) {
        PlayGames.getLeaderboardsClient(getActivity()).loadLeaderboardMetadata(true)
                .addOnSuccessListener(result -> {
                    String workingId = null;
                    try {
                        int count = result.get().getCount();
                        for (int i = 0; i < count; i++) {
                            String id = result.get().get(i).getLeaderboardId();
                            if (!BROKEN_TEST_ID.equals(id) && workingId == null) {
                                workingId = id;
                            }
                        }
                    } finally {
                        result.get().release();
                    }

                    if (workingId == null) {
                        call.reject("Working leaderboard not found");
                        return;
                    }

                    PlayGames.getLeaderboardsClient(getActivity()).getLeaderboardIntent(workingId)
                            .addOnSuccessListener(intent -> {
                                getActivity().startActivityForResult(intent, 9001);
                                call.resolve();
                            })
                            .addOnFailureListener(error -> call.reject("Open failed", error));
                })
                .addOnFailureListener(error -> call.reject("Leaderboard lookup failed", error));
    }
}
