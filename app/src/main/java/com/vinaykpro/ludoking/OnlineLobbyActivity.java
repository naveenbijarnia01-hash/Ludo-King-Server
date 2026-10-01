package com.vinaykpro.ludoking;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.socket.client.IO;
import io.socket.client.Socket;

public class OnlineLobbyActivity extends AppCompatActivity {
    // For a real phone on the PC hotspot, use the PC IPv4 address.
    public static String SERVER_URL = "https://ludo-king-server.onrender.com";

    private EditText nameInput, roomInput;
    private TextView status, roomCode, playerCount;
    private Button createRoom, joinRoom, nextBtn, startBtn;
    private LinearLayout createPanel, joinPanel, configPanel, waitingPanel;
    private Socket socket;
    private String room = "";
    private boolean host = false;
    private int stage = 0;
    private int players = 4;
    private int selectedColor = Color.RED;
    private int selectedGame = 1;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_online_lobby);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        bind();
        wireUi();
        connect();
    }

    private void bind() {
        nameInput=findViewById(R.id.nameInput); roomInput=findViewById(R.id.roomInput);
        status=findViewById(R.id.status); roomCode=findViewById(R.id.roomCode); playerCount=findViewById(R.id.playerCount);
        createRoom=findViewById(R.id.createRoom); joinRoom=findViewById(R.id.joinRoom); nextBtn=findViewById(R.id.nextBtn); startBtn=findViewById(R.id.startBtn);
        createPanel=findViewById(R.id.createPanel); joinPanel=findViewById(R.id.joinPanel); configPanel=findViewById(R.id.configPanel); waitingPanel=findViewById(R.id.waitingPanel);
    }

    private void wireUi() {
        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        createRoom.setOnClickListener(v -> { host=true; showConfig(); });
        joinRoom.setOnClickListener(v -> join());
        nextBtn.setOnClickListener(v -> { if(host && stage==2) create(); else showJoin(); });
        startBtn.setOnClickListener(v -> { if(host && socket!=null && socket.connected() && !room.isEmpty()) emitStart(); });
        findViewById(R.id.tabCreate).setOnClickListener(v -> showCreate());
        findViewById(R.id.tabJoin).setOnClickListener(v -> showJoin());
        findViewById(R.id.p2).setOnClickListener(v -> selectPlayers(2));
        findViewById(R.id.p3).setOnClickListener(v -> selectPlayers(3));
        findViewById(R.id.p4).setOnClickListener(v -> selectPlayers(4));
        findViewById(R.id.red).setOnClickListener(v -> selectColor(Color.RED));
        findViewById(R.id.green).setOnClickListener(v -> selectColor(Color.GREEN));
        findViewById(R.id.yellow).setOnClickListener(v -> selectColor(Color.YELLOW));
        findViewById(R.id.classic).setOnClickListener(v -> {selectedGame=1; markGame(true);});
        findViewById(R.id.quick).setOnClickListener(v -> {selectedGame=3; markGame(false);});
        findViewById(R.id.popular).setOnClickListener(v -> {selectedGame=4; markGame(false);});
    }

    private void connect(){
        try {
            status.setText("Connecting to multiplayer server…");
            socket=IO.socket(SERVER_URL);
            socket.on(Socket.EVENT_CONNECT,args -> runOnUiThread(() -> status.setText("Connected")));
            socket.on(Socket.EVENT_CONNECT_ERROR, args -> runOnUiThread(() ->
                    status.setText("CONNECT ERROR: " + (args.length > 0 ? String.valueOf(args[0]) : "unknown"))
            ));
            socket.on("room_state",args -> { if(args.length>0) updateRoom((JSONObject)args[0]); });
            socket.on("game_start",args -> { if(args.length>0) launchGame((JSONObject)args[0]); });
            socket.on("server_error",args -> runOnUiThread(() -> Toast.makeText(this,String.valueOf(args.length>0?args[0]:"Server error"),Toast.LENGTH_LONG).show()));
            socket.connect();
        } catch(Exception e){ status.setText("Server URL invalid"); }
    }

    private String name(){String n=nameInput.getText().toString().trim(); return n.isEmpty()?"Player":n;}
    private void create(){
        if(socket==null||!socket.connected()){Toast.makeText(this,"Server not connected",Toast.LENGTH_SHORT).show();return;}
        host=true; socket.emit("create_room", obj("name",name()));
    }
    private void join(){
        String r=roomInput.getText().toString().trim();
        if(r.length()!=8 || !r.matches("\\d{8}")){Toast.makeText(this,"Enter a valid 8-digit code",Toast.LENGTH_SHORT).show();return;}
        if(socket==null||!socket.connected()){Toast.makeText(this,"Server not connected",Toast.LENGTH_SHORT).show();return;}
        host=false; socket.emit("join_room", new JSONObjectSafe().put("room",r).put("name",name()).value());
    }
    private JSONObject obj(String k,String v){return new JSONObjectSafe().put(k,v).value();}

    private void showCreate(){stage=0;createPanel.setVisibility(View.VISIBLE);joinPanel.setVisibility(View.GONE);configPanel.setVisibility(View.GONE);waitingPanel.setVisibility(View.GONE); nextBtn.setText("NEXT");}
    private void showJoin(){stage=1;createPanel.setVisibility(View.GONE);joinPanel.setVisibility(View.VISIBLE);configPanel.setVisibility(View.GONE);waitingPanel.setVisibility(View.GONE);}
    private void showConfig(){stage=2;createPanel.setVisibility(View.GONE);joinPanel.setVisibility(View.GONE);configPanel.setVisibility(View.VISIBLE);waitingPanel.setVisibility(View.GONE); nextBtn.setText("CREATE ROOM");}
    private void selectPlayers(int p){players=p; playerCount.setText("Players: "+p+"P");}
    private void selectColor(int c){selectedColor=c;}
    private void markGame(boolean classic){ ((TextView)findViewById(R.id.gameStatus)).setText(classic?"CLASSIC selected":"Mode selected"); }

    private void updateRoom(JSONObject o){
        runOnUiThread(() -> {
            try {
                room=o.getString("room"); int count=o.getJSONArray("players").length();
                roomCode.setText(room); playerCount.setText("Players: "+count+"/4");
                if(host){showWaiting();startBtn.setEnabled(count>=2);} else {showWaiting();}
            }catch(Exception ignored){}
        });
    }
    private void showWaiting(){createPanel.setVisibility(View.GONE);joinPanel.setVisibility(View.GONE);configPanel.setVisibility(View.GONE);waitingPanel.setVisibility(View.VISIBLE);}
    private void emitStart(){ socket.emit("start_game", new JSONObjectSafe().put("room",room).value()); }

    private void launchGame(JSONObject o){
        runOnUiThread(() -> {
            try{
                JSONArray a=o.getJSONArray("players");
                Intent i=new Intent(this,MainActivity.class);
                i.putExtra("online",true); i.putExtra("room",o.getString("room")); i.putExtra("onlineColor",o.getString("color"));
                i.putExtra("nop",a.length()); i.putExtra("type",1); i.putExtra("normalPiece",true);
                for(int n=0;n<a.length();n++){JSONObject p=a.getJSONObject(n);int idx=n+1;i.putExtra("player"+idx+"name",p.getString("name"));i.putExtra("player"+idx+"color",p.getString("color"));i.putExtra("player"+idx+"bot",false);}
                startActivity(i); finish();
            }catch(Exception e){Toast.makeText(this,"Could not start game",Toast.LENGTH_LONG).show();}
        });
    }
    @Override protected void onDestroy(){if(socket!=null){socket.disconnect();socket.off();}super.onDestroy();}

    static class JSONObjectSafe {final JSONObject o=new JSONObject();JSONObjectSafe put(String k,String v){try{o.put(k,v);}catch(Exception ignored){}return this;}JSONObject value(){return o;}}
}
