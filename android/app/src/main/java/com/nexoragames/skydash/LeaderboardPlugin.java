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
    private static final String LEADERBOARD_ID = "Cgk1Mr5rcEQEAIQAw";

    @PluginMethod
    public void submitScore(PluginCall call) {
        long gameScore = call.getLong("score", 0L);
        Log.e(TAG, "METADATA DIAGNOSTIC START | gameScore=" + gameScore + " | expectedLeaderboard=" + LEADERBOARD_ID);

        PlayGames.getGamesSignInClient(getActivity())
                .isAuthenticated()
                .addOnCompleteListener(authTask -> {
                    if (authTask.isSuccessful()
                            && authTask.getResult() != null
                            && authTask.getResult().isAuthenticated()) {
                        logCurrentPlayer();
                        loadLeaderboardMetadata(call);
                    } else {
                        PlayGames.getGamesSignInClient(getActivity())
                                .signIn()
                                .addOnCompleteListener(signInTask -> {
                                    if (signInTask.isSuccessful()
                                            && signInTask.getResult() != null
                                            && signInTask.getResult().isAuthenticated()) {
                                        logCurrentPlayer();
                                        loadLeaderboardMetadata(call);
                                    } else {
                                        Exception error = signInTask.getException();
                                        logFailure("SIGN IN FAILED", error);
                                        call.reject("Google Play Games sign-in failed", error);
                                    }
                                });
                    }
                });
    }

    private void loadLeaderboardMetadata(PluginCall call) {
        Log.e(TAG, "LOAD LEADERBOARD METADATA START");

        PlayGames.getLeaderboardsClient(getActivity())
                .loadLeaderboardMetadata(true)
                .addOnSuccessListener(result -> {
                    int count = result.get().getCount();
                    Log.e(TAG, "LOAD LEADERBOARD METADATA SUCCESS | count=" + count);

                    boolean expectedFound = false;
                    for (int i = 0; i < count; i++) {
                        String id = result.get().get(i).getLeaderboardId();
                        String name = result.get().get(i).getDisplayName();
                        Log.e(TAG, "LEADERBOARD FOUND | id=" + id + " | name=" + name);
                        if (LEADERBOARD_ID.equals(id)) expectedFound = true;
                    }

                    Log.e(TAG, "EXPECTED LEADERBOARD PRESENT = " + expectedFound);
                    result.get().release();
                    call.resolve();
                })
                .addOnFailureListener(error -> {
                    logFailure("LOAD LEADERBOARD METADATA FAILED", error);
                    call.reject("Google Play Games leaderboard metadata failed: " + error.getMessage(), error);
                });
    }

    private void logCurrentPlayer() {
        PlayGames.getPlayersClient(getActivity())
                .getCurrentPlayer()
                .addOnSuccessListener(player -> Log.e(
                        TAG,
                        "CURRENT PLAYER SUCCESS | id=" + player.getPlayerId()
                                + " | name=" + player.getDisplayName()))
                .addOnFailureListener(error -> logFailure("CURRENT PLAYER FAILED", error));
    }

    private void logFailure(String prefix, Exception error) {
        if (error instanceof ApiException) {
            ApiException apiException = (ApiException) error;
            Log.e(TAG,
                    prefix
                            + " | statusCode=" + apiException.getStatusCode()
                            + " | status=" + apiException.getStatus()
                            + " | message=" + apiException.getMessage(),
                    error);
        } else if (error != null) {
            Log.e(TAG, prefix + " | type=" + error.getClass().getName()
                    + " | message=" + error.getMessage(), error);
        } else {
            Log.e(TAG, prefix + " | no exception object");
        }
    }

    @PluginMethod
    public void showLeaderboard(PluginCall call) {
        PlayGames.getLeaderboardsClient(getActivity())
                .getLeaderboardIntent(LEADERBOARD_ID)
                .addOnSuccessListener(intent -> {
                    getActivity().startActivityForResult(intent, 9001);
                    call.resolve();
                })
                .addOnFailureListener(error -> {
                    logFailure("OPEN LEADERBOARD FAILED", error);
                    call.reject("Failed to open Google Play leaderboard: " + error.getMessage(), error);
                });
    }
}
