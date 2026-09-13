package com.nexoragames.skydash;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.games.PlayGames;

@CapacitorPlugin(name = "Leaderboard")
public class LeaderboardPlugin extends Plugin {

    private static final String LEADERBOARD_ID = "Cgk1Mr5rcEQEAIQAw";

 @PluginMethod
    public void submitScore(PluginCall call) {
        long score = call.getLong("score", 0L);
android.util.Log.e("SkyDashGPGS", "SUBMIT SCORE VALUE = " + score);
        PlayGames.getGamesSignInClient(getActivity())
            .isAuthenticated()
            .addOnCompleteListener(authTask -> {
                if (authTask.isSuccessful() && authTask.getResult().isAuthenticated()) {
                    PlayGames.getPlayersClient(getActivity())
        .getCurrentPlayer()
        .addOnSuccessListener(player -> {
            android.util.Log.e("SkyDashGPGS",
                    "CURRENT PLAYER SUCCESS | id=" + player.getPlayerId()
                    + " | name=" + player.getDisplayName());
        })
        .addOnFailureListener(error -> {
            android.util.Log.e("SkyDashGPGS",
                    "CURRENT PLAYER FAILED | "
                    + error.getClass().getName()
                    + " | message=" + error.getMessage());
        });
                    PlayGames.getLeaderboardsClient(getActivity())
                       .submitScore(LEADERBOARD_ID, score);
                       call.resolve();
                } else {
                    PlayGames.getGamesSignInClient(getActivity())
                        .signIn()
                        .addOnCompleteListener(signInTask -> {
                            if (signInTask.isSuccessful() && signInTask.getResult().isAuthenticated()) {
                         PlayGames.getLeaderboardsClient(getActivity())
        .submitScore(LEADERBOARD_ID, score);
call.resolve();
                            } else {
                                call.reject("Google Play Games sign-in failed");
                            }
                        });
                }
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
                call.reject("Failed to open Google Play leaderboard: " + error.getMessage());
            });
}
}