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
    private static final String LEADERBOARD_ID = "Cgk1Mr5rcEQEAIQAw";

    @PluginMethod
    public void submitScore(PluginCall call) {
        long score = call.getLong("score", 0L);
        Log.e(TAG, "SUBMIT SCORE VALUE = " + score + " | leaderboard=" + LEADERBOARD_ID);

        PlayGames.getGamesSignInClient(getActivity())
                .isAuthenticated()
                .addOnCompleteListener(authTask -> {
                    if (authTask.isSuccessful()
                            && authTask.getResult() != null
                            && authTask.getResult().isAuthenticated()) {
                        logCurrentPlayer();
                        submitScoreImmediate(call, score);
                        return;
                    }

                    Log.e(TAG, "NOT AUTHENTICATED - attempting sign in");
                    PlayGames.getGamesSignInClient(getActivity())
                            .signIn()
                            .addOnCompleteListener(signInTask -> {
                                if (signInTask.isSuccessful()
                                        && signInTask.getResult() != null
                                        && signInTask.getResult().isAuthenticated()) {
                                    Log.e(TAG, "SIGN IN SUCCESS");
                                    logCurrentPlayer();
                                    submitScoreImmediate(call, score);
                                } else {
                                    Exception error = signInTask.getException();
                                    logFailure("SIGN IN FAILED", error);
                                    call.reject("Google Play Games sign-in failed", error);
                                }
                            });
                });
    }

    private void submitScoreImmediate(PluginCall call, long score) {
        Log.e(TAG, "SUBMIT IMMEDIATE START | score=" + score + " | leaderboard=" + LEADERBOARD_ID);

        PlayGames.getLeaderboardsClient(getActivity())
                .submitScoreImmediate(LEADERBOARD_ID, score)
                .addOnSuccessListener(result -> {
                    Log.e(TAG, "SUBMIT IMMEDIATE SUCCESS | score=" + score);

                    JSObject response = new JSObject();
                    response.put("submitted", true);
                    response.put("score", score);
                    response.put("leaderboardId", LEADERBOARD_ID);
                    call.resolve(response);
                })
                .addOnFailureListener(error -> {
                    logFailure("SUBMIT IMMEDIATE FAILED", error);
                    call.reject("Google Play Games score submission failed: " + error.getMessage(), error);
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
            Log.e(
                    TAG,
                    prefix
                            + " | statusCode=" + apiException.getStatusCode()
                            + " | status=" + apiException.getStatus()
                            + " | message=" + apiException.getMessage(),
                    error);
        } else if (error != null) {
            Log.e(
                    TAG,
                    prefix
                            + " | type=" + error.getClass().getName()
                            + " | message=" + error.getMessage(),
                    error);
        } else {
            Log.e(TAG, prefix + " | no exception object");
        }
    }

    @PluginMethod
    public void showLeaderboard(PluginCall call) {
        Log.e(TAG, "OPEN LEADERBOARD START | leaderboard=" + LEADERBOARD_ID);

        PlayGames.getLeaderboardsClient(getActivity())
                .getLeaderboardIntent(LEADERBOARD_ID)
                .addOnSuccessListener(intent -> {
                    Log.e(TAG, "OPEN LEADERBOARD INTENT SUCCESS");
                    getActivity().startActivityForResult(intent, 9001);
                    call.resolve();
                })
                .addOnFailureListener(error -> {
                    logFailure("OPEN LEADERBOARD FAILED", error);
                    call.reject("Failed to open Google Play leaderboard: " + error.getMessage(), error);
                });
    }
}
