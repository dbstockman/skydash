package com.nexoragames.skydash;

import android.util.Log;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.games.PlayGames;

@CapacitorPlugin(name = "Leaderboard")
public class LeaderboardPlugin extends Plugin {
    private static final String TAG = "SkyDashGPGS";
    private static final String TEST_ID = "Cgk1Mr5rcEQEAIQAw";

    @PluginMethod
    public void submitScore(PluginCall call) {
        PlayGames.getGamesSignInClient(getActivity()).isAuthenticated().addOnCompleteListener(auth -> {
            if (auth.isSuccessful() && auth.getResult() != null && auth.getResult().isAuthenticated()) {
                testOtherLeaderboard(call);
            } else {
                call.reject("Not authenticated");
            }
        });
    }

    private void testOtherLeaderboard(PluginCall call) {
        PlayGames.getLeaderboardsClient(getActivity()).loadLeaderboardMetadata(true)
                .addOnSuccessListener(result -> {
                    String otherId = null;
                    String otherName = null;
                    try {
                        int count = result.get().getCount();
                        for (int i = 0; i < count; i++) {
                            String id = result.get().get(i).getLeaderboardId();
                            String name = result.get().get(i).getDisplayName();
                            Log.e(TAG, "FOUND | id=" + id + " | name=" + name);
                            if (!TEST_ID.equals(id) && otherId == null) {
                                otherId = id;
                                otherName = name;
                            }
                        }
                    } finally {
                        result.get().release();
                    }

                    if (otherId == null) {
                        call.reject("Alternate leaderboard not found");
                        return;
                    }

                    Log.e(TAG, "TEST OTHER | id=" + otherId + " | name=" + otherName);
                    String finalId = otherId;
                    PlayGames.getLeaderboardsClient(getActivity()).submitScoreImmediate(finalId, 100L)
                            .addOnSuccessListener(scoreResult -> {
                                Log.e(TAG, "OTHER SUBMIT SUCCESS | id=" + finalId);
                                call.resolve();
                            })
                            .addOnFailureListener(error -> {
                                logFailure("OTHER SUBMIT FAILED", error);
                                call.reject("Alternate leaderboard failed: " + error.getMessage(), error);
                            });
                })
                .addOnFailureListener(error -> {
                    logFailure("METADATA FAILED", error);
                    call.reject("Metadata failed: " + error.getMessage(), error);
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
        PlayGames.getLeaderboardsClient(getActivity()).getLeaderboardIntent(TEST_ID)
                .addOnSuccessListener(intent -> {
                    getActivity().startActivityForResult(intent, 9001);
                    call.resolve();
                })
                .addOnFailureListener(error -> call.reject("Open failed", error));
    }
}
