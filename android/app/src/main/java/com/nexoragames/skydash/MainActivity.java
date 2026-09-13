package com.nexoragames.skydash;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.games.GamesSignInClient;
import com.google.android.gms.games.PlayGamesSdk;
public class MainActivity extends BridgeActivity {
    private GamesSignInClient gamesSignInClient;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(LeaderboardPlugin.class);
        super.onCreate(savedInstanceState);
PlayGamesSdk.initialize(this);

        gamesSignInClient = PlayGames.getGamesSignInClient(this);

        gamesSignInClient.isAuthenticated().addOnCompleteListener(task -> {
            if (task.isSuccessful() && !task.getResult().isAuthenticated()) {
                gamesSignInClient.signIn();
            }
        });
    }
}

