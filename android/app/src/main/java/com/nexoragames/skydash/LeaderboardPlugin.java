package com.nexoragames.skydash;

import android.util.Log;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.games.leaderboard.LeaderboardVariant;

@CapacitorPlugin(name = "Leaderboard")
public class LeaderboardPlugin extends Plugin {

    private static final String TAG = "SkyDashGPGS";
    private static final String LEADERBOARD_ID = "Cgk1Mr5rcEQEAIQAw";

    @PluginMethod
    public void submitScore(PluginCall call) {
        long gameScore = call.getLong("score", 0L);
        Log.e(TAG, "READ DIAGNOSTIC START | gameScore=" + gameScore + " | leaderboard=" + LEADERBOARD_ID);

        PlayGames.getGamesSignInClient(getActivity())
                .isAuthenticated()
                .addOnCompleteListener(authTask -> {
                    if (authTask.isSuccessful()
                            && authTask.getResult() != null
                            && authTask.getResult().isAuthenticated()) {
                        logCurrentPlayer();
                        loadTopScores(call);
                    } else {
                        PlayGames.getGamesSignInClient(getActivity())
                                .signIn()
                                .addOnCompleteListener(signInTask -> {
                                    if (signInTask.isSuccessful()
                                            && signInTask.getResult() != null
                                            && signInTask.getResult().isAuthenticated()) {
                                        logCurrentPlayer();
                                        loadTopScores(call);
                                    } else {
                                        Exception error = signInTask.getException();
                                        logFailure("SIGN IN FAILED", error);
                                        call.reject("Google Play Games sign-in failed", error);
                                    }
                                });
                    }
                });
    }

    private void loadTopScores(PluginCall call) {
        Log.e(TAG, "LOAD TOP SCORES START | leaderboard=" + LEADERBOARD_ID);

        PlayGames.getLeaderboardsClient(getActivity())
                .loadTopScores(
                        LEADERBOARD_ID,
                        LeaderboardVariant.TIME_SPAN_ALL_TIME,
                        LeaderboardVariant.COLLECTION_PUBLIC,
                        10,
                        true)
                .addOnSuccessListener(result -> {
                    Log.e(TAG, "LOAD TOP SCORES SUCCESS | leaderboard=" + LEADERBOARD_ID);
                    call.resolve();
                })
                .addOnFailureListener(error -> {
                    logFailure("LOAD TOP SCORES FAILED", error);
                    call.reject("Google Play Games leaderboard read failed: " + error.getMessage(), error);
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
