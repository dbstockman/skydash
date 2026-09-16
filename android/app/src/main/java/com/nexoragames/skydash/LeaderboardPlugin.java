package com.nexoragames.skydash;

import android.util.Log;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.games.LeaderboardsClient;
import com.google.android.gms.games.PageDirection;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.games.leaderboard.LeaderboardScore;
import com.google.android.gms.games.leaderboard.LeaderboardScoreBuffer;
import com.google.android.gms.games.leaderboard.LeaderboardVariant;

import java.util.HashSet;
import java.util.Set;

@CapacitorPlugin(name = "Leaderboard")
public class LeaderboardPlugin extends Plugin {

    private static final String TAG = "SkyDashGPGS";
    private static final String LEADERBOARD_ID = "CgkI1Mr5rcEQEAIQAQ";
    private static final int TOP_SCORE_LIMIT = 100;
    private static final int SCORES_PER_PAGE = 25;

    @PluginMethod
    public void submitScore(PluginCall call) {
        Object rawScore = call.getData().opt("score");
        long score = rawScore instanceof Number ? ((Number) rawScore).longValue() : 0L;
        Log.e(TAG, "RAW SCORE RECEIVED | raw=" + rawScore + " | parsed=" + score);

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
        PlayGames.getLeaderboardsClient(getActivity())
                .submitScoreImmediate(LEADERBOARD_ID, score)
                .addOnSuccessListener(result -> {
                    Log.e(TAG, "SCORE SUBMIT SUCCESS | score=" + score);

                    JSObject response = new JSObject();
                    response.put("submitted", true);
                    response.put("score", score);
                    response.put("leaderboardId", LEADERBOARD_ID);
                    call.resolve(response);
                })
                .addOnFailureListener(error -> {
                    logFailure("SCORE SUBMIT FAILED", error);
                    call.reject("Google Play Games score submission failed: " + error.getMessage(), error);
                });
    }

    @PluginMethod
    public void loadScores(PluginCall call) {
        LeaderboardsClient client = PlayGames.getLeaderboardsClient(getActivity());
        client.loadTopScores(
                        LEADERBOARD_ID,
                        LeaderboardVariant.TIME_SPAN_ALL_TIME,
                        LeaderboardVariant.COLLECTION_PUBLIC,
                        SCORES_PER_PAGE,
                        true)
                .addOnSuccessListener(data -> {
                    LeaderboardsClient.LeaderboardScores firstPage = data.get();
                    if (firstPage == null) {
                        call.reject("Google Play Games returned no leaderboard scores");
                        return;
                    }

                    JSArray scores = new JSArray();
                    Set<Long> seenRanks = new HashSet<>();
                    loadScorePage(client, firstPage, scores, seenRanks, call, 1);
                })
                .addOnFailureListener(error -> {
                    logFailure("LEADERBOARD LOAD FAILED", error);
                    call.reject("Failed to load Google Play leaderboard scores: " + error.getMessage(), error);
                });
    }

    private void loadScorePage(
            LeaderboardsClient client,
            LeaderboardsClient.LeaderboardScores page,
            JSArray scores,
            Set<Long> seenRanks,
            PluginCall call,
            int pageNumber) {

        LeaderboardScoreBuffer scoreBuffer = page.getScores();
        int scoreCountBeforePage = scores.length();

        for (int i = 0; i < scoreBuffer.getCount() && scores.length() < TOP_SCORE_LIMIT; i++) {
            LeaderboardScore entry = scoreBuffer.get(i);
            long rank = entry.getRank();

            if (seenRanks.add(rank)) {
                JSObject row = new JSObject();
                row.put("name", entry.getScoreHolderDisplayName());
                row.put("score", entry.getRawScore());
                row.put("rank", rank);
                scores.put(row);
            }
        }

        boolean reachedTop100 = scores.length() >= TOP_SCORE_LIMIT;
        boolean noScoresOnPage = scoreBuffer.getCount() == 0;
        boolean noNewScores = scores.length() == scoreCountBeforePage;
        boolean reachedFourPages = pageNumber >= 4;

        if (reachedTop100 || noScoresOnPage || noNewScores || reachedFourPages) {
            page.release();
            finishLeaderboardResponse(call, scores);
            return;
        }

        client.loadMoreScores(scoreBuffer, SCORES_PER_PAGE, PageDirection.NEXT)
                .addOnSuccessListener(nextData -> {
                    page.release();
                    LeaderboardsClient.LeaderboardScores nextPage = nextData.get();
                    if (nextPage == null) {
                        finishLeaderboardResponse(call, scores);
                        return;
                    }
                    loadScorePage(client, nextPage, scores, seenRanks, call, pageNumber + 1);
                })
                .addOnFailureListener(error -> {
                    page.release();
                    logFailure("LEADERBOARD PAGE LOAD FAILED", error);
                    call.reject("Failed to load additional Google Play leaderboard scores: " + error.getMessage(), error);
                });
    }

    private void finishLeaderboardResponse(PluginCall call, JSArray scores) {
        PlayGames.getPlayersClient(getActivity())
                .getCurrentPlayer()
                .addOnSuccessListener(player -> {
                    JSObject response = new JSObject();
                    response.put("scores", scores);
                    response.put("leaderboardId", LEADERBOARD_ID);
                    response.put("currentPlayerName", player == null ? "" : player.getDisplayName());
                    Log.e(TAG, "LEADERBOARD LOAD SUCCESS | count=" + scores.length()
                            + " | currentPlayer=" + (player == null ? "null" : player.getDisplayName()));
                    call.resolve(response);
                })
                .addOnFailureListener(playerError -> {
                    Log.w(TAG, "CURRENT PLAYER LOOKUP FAILED | " + playerError.getMessage());
                    JSObject response = new JSObject();
                    response.put("scores", scores);
                    response.put("leaderboardId", LEADERBOARD_ID);
                    response.put("currentPlayerName", "");
                    call.resolve(response);
                });
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
                    call.reject("Failed to open Google Play leaderboard", error);
                });
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
}
