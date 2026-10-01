const { Server } = require("socket.io");
const http = require("http");
const crypto = require("crypto");

const PORT = process.env.PORT || 3000;
const httpServer = http.createServer();
const io = new Server(httpServer, { cors: { origin: "*" } });
const rooms = new Map();
const COLORS = ["red", "green", "yellow", "blue"];

function generateRoomCode() {
    return crypto.randomInt(10000000, 100000000).toString();
}

function state(r){ return {room:r.code, host:r.host===r.players[0]?.id, players:r.players.map(p=>({name:p.name,color:p.color,id:p.id}))}; }
function sendState(r){ r.players.forEach(p=>io.to(p.id).emit("room_state", state(r))); }

io.on("connection", socket=>{
  socket.on("create_room", ({name})=>{
    let c; do c=generateRoomCode(); while(rooms.has(c));
    const r={code:c,host:socket.id,started:false,players:[]};
    r.players.push({id:socket.id,name:String(name||"Player").slice(0,20),color:null});
    rooms.set(c,r); socket.join(c); socket.data.room=c; sendState(r);
  });

  socket.on("join_room", ({room,name})=>{
    const r=rooms.get(String(room||"").toUpperCase());
    if(!r) return socket.emit("server_error","Room not found");
    if(r.started) return socket.emit("server_error","Game already started");
    if(r.players.length>=4) return socket.emit("server_error","Room is full");
    r.players.push({id:socket.id,name:String(name||"Player").slice(0,20),color:null});
    socket.join(r.code); socket.data.room=r.code; sendState(r);
  });

  socket.on("start_game", ({room})=>{
    const r=rooms.get(String(room||"").toUpperCase());
    if(!r || r.host!==socket.id || r.players.length<2) return;
    const palettes = {2:["green","blue"],3:["green","yellow","blue"],4:["green","yellow","blue","red"]};
    const colors = palettes[r.players.length];
    r.players.forEach((p,i)=>p.color=colors[i]);
    const anchor = r.players.length===3 ? "red" : "green";
    r.started=true;
    r.players.forEach(p=>io.to(p.id).emit("game_start",{room:r.code,anchor,color:p.color,players:r.players.map(x=>({name:x.name,color:x.color}))}));
  });

  // Client-generated dice is intentionally authoritative for this prototype.
  socket.on("roll", ({room,color,value})=>{
    const r=rooms.get(String(room||"").toUpperCase()); if(!r||!r.started) return;
    const n=Math.max(1,Math.min(6,Number(value)||1));
    io.to(r.code).emit("dice_roll",{color:String(color),value:n});
  });

  socket.on("piece_action", ({room,color,piece,type,dice})=>{
    const r=rooms.get(String(room||"").toUpperCase()); if(!r||!r.started) return;
    socket.to(r.code).emit("piece_action",{color:String(color),piece:Number(piece),type:String(type),dice:Number(dice)||0});
  });

  socket.on("disconnect",()=>{
    const c=socket.data.room; const r=rooms.get(c); if(!r) return;
    r.players=r.players.filter(p=>p.id!==socket.id);
    if(r.players.length===0){rooms.delete(c);return;}
    if(r.host===socket.id) r.host=r.players[0].id;
    if(!r.started) sendState(r);
  });
});

httpServer.listen(PORT,()=>console.log(`Ludo multiplayer server listening on :${PORT}`));
