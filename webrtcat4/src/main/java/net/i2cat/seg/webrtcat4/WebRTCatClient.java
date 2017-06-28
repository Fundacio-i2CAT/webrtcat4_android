/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package net.i2cat.seg.webrtcat4;

import android.util.Log;

import net.i2cat.seg.webrtcat4.WebRTCatSocketChannelClient.WebSocketConnectionState;
import net.i2cat.seg.webrtcat4.stats.WebRTStats;

import org.appspot.apprtc.AppRTCClient;
import org.appspot.apprtc.RoomParametersFetcher;
import org.appspot.apprtc.RoomParametersFetcher.RoomParametersFetcherEvents;
import org.appspot.apprtc.util.AsyncHttpURLConnection;
import org.appspot.apprtc.util.AsyncHttpURLConnection.AsyncHttpEvents;
import org.appspot.apprtc.util.LooperExecutor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import static net.i2cat.seg.webrtcat4.WebRTUtils.jsonPut;

/**
 * Negotiates signaling for chatting with apprtc.appspot.com "rooms".
 * Uses the client<->server specifics of the apprtc AppEngine webapp.
 *
 * <p>To use: create an instance of this object (registering a message handler) and
 * call connectToRoom().  Once room connection is established
 * onConnectedToRoom() callback with room parameters is invoked.
 * Messages to other party (with local Ice candidates and answer SDP) can
 * be sent after WebSocket connection is established.
 */
public class WebRTCatClient implements AppRTCClient,
    WebRTCatSocketChannelClient.WebSocketChannelEvents {
    private static final String TAG = "WebRTCatClient";
    private static final String ROOM_JOIN = "join";
    private static final String ROOM_MESSAGE = "message";
    private static final String ROOM_LEAVE = "leave";

    private enum ConnectionState {
        NEW, CONNECTED, CLOSED, ERROR
    }
    private enum MessageType {
        MESSAGE, LEAVE
    }

    private final LooperExecutor executor;
    private boolean initiator;
    private SignalingEvents events;
    private WebRTCatSocketChannelClient wsClient;
    private ConnectionState roomState;
    private RoomConnectionParameters connectionParameters;
    private String messageUrl;
    private String leaveUrl;
    private SignalingParameters signalingParameters;
    private String clientName;
    private boolean isConnectedToRoom;
    private WebRTCat.DisconnectReason disconnectReason;
    private WebRTStats lastStats;

    interface SignalingEvents extends AppRTCClient.SignalingEvents {
      /**
       * Added to provide a way to richer reporting of error events while communicating with signaling
       * (instead of just a human-readable error message)
       */
      void onChannelError(String description, WebRTCatErrorCode errorCode);
    }


  public WebRTCatClient(WebRTCatClient.SignalingEvents events, LooperExecutor executor, String clientName) {
    this.events = events;
    this.executor = executor;
    this.clientName = clientName;
    roomState = ConnectionState.NEW;
    executor.requestStart();
  }

  // --------------------------------------------------------------------
  // AppRTCClient interface implementation.
  // Asynchronously connect to an AppRTC room URL using supplied connection
  // parameters, retrieves room parameters and connect to WebSocket server.
  @Override
  public void connectToRoom(RoomConnectionParameters connectionParameters) {
    this.connectionParameters = connectionParameters;
    executor.execute(new Runnable() {
      @Override
      public void run() {
        connectToRoomInternal();
      }
    });
  }

  @Override
  public void disconnectFromRoom() {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        disconnectFromRoomInternal();
      }
    });
    executor.requestStop();
  }

  public void setDisconnectReason(WebRTCat.DisconnectReason reason) {
    this.disconnectReason = reason;
  }

  public void setLastStats(WebRTStats stats) {
    this.lastStats = stats;
  }

  // Connects to room - function runs on a local looper thread.
  private void connectToRoomInternal() {
    String connectionUrl = getConnectionUrl(connectionParameters);
    Log.d(TAG, "Connect to room: " + connectionUrl);
    roomState = ConnectionState.NEW;
    wsClient = new WebRTCatSocketChannelClient(executor, this);

    RoomParametersFetcherEvents callbacks = new RoomParametersFetcherEvents() {
      @Override
      public void onSignalingParametersReady(
          final SignalingParameters params) {
        WebRTCatClient.this.executor.execute(new Runnable() {
          @Override
          public void run() {
            WebRTCatClient.this.signalingParametersReady(params);
          }
        });
      }

      @Override
      public void onSignalingParametersError(String description) {
        WebRTCatClient.this.reportError(description, WebRTCatErrorCode.CANT_JOIN_ROOM);
      }
    };

    new RoomParametersFetcher(connectionUrl, null, callbacks).makeRequest();
  }

  // Disconnect from room and send bye messages - runs on a local looper thread.
  private void disconnectFromRoomInternal() {
    Log.d(TAG, "Disconnect. Room state: " + roomState);
    if (isConnectedToRoom) {
      Log.d(TAG, "Closing room.");
      JSONObject json = new JSONObject();
      jsonPut(json, "disconnectReason", (disconnectReason != null) ? disconnectReason.toString() : "");
      if (lastStats != null) {
        jsonPut(json, "clientStats", lastStats.toJSON());
      }
      sendPostMessage(MessageType.LEAVE, leaveUrl, json.toString());
    }
    roomState = ConnectionState.CLOSED;
    if (wsClient != null) {
      wsClient.disconnect(true);
    }
  }

  // Helper functions to get connection, post message and leave message URLs
  private String getConnectionUrl(
      RoomConnectionParameters connectionParameters) {
    return connectionParameters.roomUrl + "/" + ROOM_JOIN + "/"
        + connectionParameters.roomId;
  }

  private String getMessageUrl(RoomConnectionParameters connectionParameters,
      SignalingParameters signalingParameters) {
    return connectionParameters.roomUrl + "/" + ROOM_MESSAGE + "/"
      + connectionParameters.roomId + "/" + signalingParameters.clientId;
  }

  private String getLeaveUrl(RoomConnectionParameters connectionParameters,
      SignalingParameters signalingParameters) {
    String url = connectionParameters.roomUrl + "/" + ROOM_LEAVE + "/"
        + connectionParameters.roomId + "/" + signalingParameters.clientId;
    if (clientName != null) {
      url = url + "/" + clientName;
    }
    return url;
  }

    // Callback issued when room parameters are extracted. Runs on local
    // looper thread.
    private void signalingParametersReady(final SignalingParameters signalingParameters) {
        Log.d(TAG, "Room connection completed.");
        if (connectionParameters.loopback
        && (!signalingParameters.initiator
            || signalingParameters.offerSdp != null)) {
                reportError("Loopback room is busy.", WebRTCatErrorCode.GENERAL_ERROR);
                return;
        }
        if (!connectionParameters.loopback
            && !signalingParameters.initiator
            && signalingParameters.offerSdp == null) {
            Log.w(TAG, "No offer SDP in room response.");
        }
        initiator = signalingParameters.initiator;
        messageUrl = getMessageUrl(connectionParameters, signalingParameters);
        leaveUrl = getLeaveUrl(connectionParameters, signalingParameters);
        this.signalingParameters = signalingParameters;
        Log.d(TAG, "Message URL: " + messageUrl);
        Log.d(TAG, "Leave URL: " + leaveUrl);
        roomState = ConnectionState.CONNECTED;
        isConnectedToRoom = true;

        // onWebSocketOpen() will be called when this is successful.
        wsClient.connect(signalingParameters.wssUrl, signalingParameters.wssPostUrl);
    }

  @Override
  public void sendOfferSdp(final SessionDescription sdp) {
    sendOfferSdp(sdp, null);
  }

  // Send local offer SDP to the other participant.
  public void sendOfferSdp(final SessionDescription sdp, final String destClientName) {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        if (roomState != ConnectionState.CONNECTED) {
          reportError("Sending offer SDP in non connected state.", WebRTCatErrorCode.INTERNAL_STATE_MACHINE_ERROR);
          return;
        }
        JSONObject json = new JSONObject();
        jsonPut(json, "sdp", sdp.description);
        jsonPut(json, "type", "offer");
        if (destClientName != null) {
          jsonPut(json, "sourceClientName", clientName);
          jsonPut(json, "destClientName", destClientName);
        }
        sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
        if (connectionParameters.loopback) {
          // In loopback mode rename this offer to answer and route it back.
          SessionDescription sdpAnswer = new SessionDescription(
              SessionDescription.Type.fromCanonicalForm("answer"),
              sdp.description);
          events.onRemoteDescription(sdpAnswer);
        }
      }
    });
  }

  @Override
  public void sendAnswerSdp(final SessionDescription sdp) {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        if (connectionParameters.loopback) {
          Log.e(TAG, "Sending answer in loopback mode.");
          return;
        }
        JSONObject json = new JSONObject();
        jsonPut(json, "sdp", sdp.description);
        jsonPut(json, "type", "answer");
        wsClient.send(json.toString());

        // Send a system:answer message to the room server to indicate that we are answering a call.
        json = new JSONObject();
        jsonPut(json, "type", "system:answer");
        jsonPut(json, "sourceClientName", clientName);
        sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
      }
    });
  }

  // Send Ice candidate to the other participant.
  @Override
  public void sendLocalIceCandidate(final IceCandidate candidate) {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        JSONObject json = new JSONObject();
        jsonPut(json, "type", "candidate");
        jsonPut(json, "label", candidate.sdpMLineIndex);
        jsonPut(json, "id", candidate.sdpMid);
        jsonPut(json, "candidate", candidate.sdp);
        if (initiator) {
          // Call initiator sends ice candidates to room server.
          if (roomState != ConnectionState.CONNECTED) {
            reportError("Sending ICE candidate in non connected state.", WebRTCatErrorCode.INTERNAL_STATE_MACHINE_ERROR);
            return;
          }
          sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
          if (connectionParameters.loopback) {
            events.onRemoteIceCandidate(candidate);
          }
        } else {
          // Call receiver sends ice candidates to websocket server.
          wsClient.send(json.toString());
        }
      }
    });
  }

  @Override
  public void sendLocalIceCandidateRemovals(final IceCandidate[] candidates) {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        JSONObject json = new JSONObject();
        jsonPut(json, "type", "remove-candidates");
        JSONArray jsonArray =  new JSONArray();
        for (final IceCandidate candidate : candidates) {
          jsonArray.put(toJsonCandidate(candidate));
        }
        jsonPut(json, "candidates", jsonArray);
        if (initiator) {
          // Call initiator sends ice candidates to GAE server.
          if (roomState != ConnectionState.CONNECTED) {
            reportError("Sending ICE candidate removals in non connected state.", WebRTCatErrorCode.INTERNAL_STATE_MACHINE_ERROR);
          return;
        }
          sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
          if (connectionParameters.loopback) {
            events.onRemoteIceCandidatesRemoved(candidates);
          }
        } else {
          // Call receiver sends ice candidates to websocket server.
          wsClient.send(json.toString());
        }
       }
    });
  }

  // --------------------------------------------------------------------
  // WebSocketChannelEvents interface implementation.
  // All events are called by WebRTCatSocketChannelClient on a local looper thread
  // (passed to WebSocket client constructor).
    @Override
    public void onWebSocketOpen() {
        // Now we can register this client.
        wsClient.register(connectionParameters.roomId, signalingParameters.clientId);
    }

    @Override
    public void onWebSocketRegistered() {
        // Finally, with the socket open and the client registered can we consider ourselves truly
        // connected to the room.
        events.onConnectedToRoom(signalingParameters);
    }

    @Override
    public void onWebSocketMessage(final String msg) {
        if (wsClient.getState() != WebSocketConnectionState.REGISTERED) {
          Log.e(TAG, "Got WebSocket message in non registered state.");
          return;
        }
        try {
          JSONObject json = new JSONObject(msg);
          String msgText = json.getString("msg");
          String errorText = json.optString("error");
          if (msgText.length() > 0) {
            json = new JSONObject(msgText);
            String type = json.optString("type");
            if (type.equals("candidate")) {
              events.onRemoteIceCandidate(toJavaCandidate(json));
            } else if (type.equals("remove-candidates")) {
              JSONArray candidateArray = json.getJSONArray("candidates");
              IceCandidate[] candidates = new IceCandidate[candidateArray.length()];
              for (int i = 0; i < candidateArray.length(); ++i) {
                candidates[i] = toJavaCandidate(candidateArray.getJSONObject(i));
              }
              events.onRemoteIceCandidatesRemoved(candidates);
            } else if (type.equals("answer")) {
              if (initiator) {
                SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(type),
                    json.getString("sdp"));
                events.onRemoteDescription(sdp);
              } else {
                reportError("Received answer for call initiator: " + msg, WebRTCatErrorCode.INTERNAL_STATE_MACHINE_ERROR);
              }
            } else if (type.equals("offer")) {
              if (!initiator) {
                SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(type),
                    json.getString("sdp"));
                events.onRemoteDescription(sdp);
              } else {
                reportError("Received offer for call receiver: " + msg, WebRTCatErrorCode.INTERNAL_STATE_MACHINE_ERROR);
              }
            } else if (type.equals("bye")) {
              events.onChannelClose();
            } else {
              reportError("Unexpected WebSocket message: " + msg, WebRTCatErrorCode.UNKNOWN_SIGNALING_SERVER_MESSAGE);
            }
          } else {
            if (errorText != null && errorText.length() > 0) {
              reportError("WebSocket error message: " + errorText, WebRTCatErrorCode.SIGNALING_SERVER_REPORTED_ERROR);
            } else {
              reportError("Unexpected WebSocket message: " + msg, WebRTCatErrorCode.UNKNOWN_SIGNALING_SERVER_MESSAGE);
            }
          }
        } catch (JSONException e) {
          reportError("WebSocket message JSON parsing error: " + e.toString(), WebRTCatErrorCode.UNKNOWN_SIGNALING_SERVER_MESSAGE);
        }
    }

    @Override
    public void onWebSocketClose() {
        events.onChannelClose();
    }

    @Override
    public void onWebSocketError(String description, WebRTCatErrorCode errorCode) {
        reportError("WebSocket error: " + description, errorCode);
    }

  // --------------------------------------------------------------------
  // Helper functions.
  private void reportError(final String errorMessage, final WebRTCatErrorCode errorCode) {
    Log.e(TAG, errorMessage);
    executor.execute(new Runnable() {
      @Override
      public void run() {
        if (roomState != ConnectionState.ERROR) {
          roomState = ConnectionState.ERROR;
          events.onChannelError(errorMessage, errorCode);
        }
      }
    });
  }

  // Send SDP or ICE candidate to a room server.
  private void sendPostMessage(
      final MessageType messageType, final String url, final String message) {
    String logInfo = url;
    if (message != null) {
      logInfo += ". Message: " + message;
    }
    Log.d(TAG, "C->RoomServer: " + logInfo);
    AsyncHttpURLConnection httpConnection = new AsyncHttpURLConnection(
      "POST", url, message, new AsyncHttpEvents() {
        @Override
        public void onHttpError(String errorMessage) {
          reportError("RoomServer POST error: " + errorMessage, (messageType == MessageType.MESSAGE) ? WebRTCatErrorCode.CANT_MESSAGE_ROOM : WebRTCatErrorCode.GENERAL_ERROR);
        }

        @Override
        public void onHttpComplete(String response) {
          if (messageType == MessageType.MESSAGE) {
            try {
              JSONObject roomJson = new JSONObject(response);
              String result = roomJson.getString("result");
              if (!result.equals("SUCCESS")) {
                reportError(result, WebRTCatErrorCode.CANT_MESSAGE_ROOM);
              }
            } catch (JSONException e) {
              reportError("RoomServer POST JSON error: " + e.toString(), WebRTCatErrorCode.CANT_MESSAGE_ROOM);
            }
          }
        }
      });
    httpConnection.send();
  }

  // Converts a Java candidate to a JSONObject.
  private JSONObject toJsonCandidate(final IceCandidate candidate) {
    JSONObject json = new JSONObject();
    jsonPut(json, "label", candidate.sdpMLineIndex);
    jsonPut(json, "id", candidate.sdpMid);
    jsonPut(json, "candidate", candidate.sdp);
    return json;
  }

  // Converts a JSON candidate to a Java object.
  IceCandidate toJavaCandidate(JSONObject json) throws JSONException {
    return new IceCandidate(json.getString("id"),
                            json.getInt("label"),
                            json.getString("candidate"));
  }
}
