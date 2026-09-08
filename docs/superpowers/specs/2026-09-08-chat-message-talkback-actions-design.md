# Design: TalkBack Accessibility Actions for Chat Messages

## Summary
Provide comprehensive accessibility (TalkBack) custom action support for messages inside chat screens (`ChatActivity`), bringing full feature parity with the accessibility actions implemented for dialogues in `DialogsActivity`.

## Requirements
1. **Dynamic Action Exposure**: Chat messages must expose standard message actions via accessibility node actions:
   - **Copy**: Copy text, captions, or formatted message content to the clipboard.
   - **Reply**: Attach the message to the enter view reply preview.
   - **Forward**: Open the forward dialog with the message pre-selected.
   - **Open Reactions Panel**: Directly open the expanded emoji reaction window for the message.
   - **Edit**: Enter editing mode for editable messages.
   - **Pin / Unpin**: Pin or unpin the message according to chat permissions and its current pinned state.
   - **Delete**: Trigger the delete confirmation dialog.
2. **Selection / Multi-Select (Action Mode)**:
   - When not in action mode, provide an action to enter selection mode ("Enter selection mode").
   - When action mode is active:
     - Mark the message cell as checkable (`info.setCheckable(true)`) and report its checked state (`info.setChecked(...)`).
     - Expose "Select" or "Deselect" via `acc_action_toggle_selection`.
     - When multiple messages are selected, expose bulk operations ("Delete", "Forward", "Copy").
3. **Architectural Consistency**:
   - Follow the established delegate pattern: `ChatMessageCell` delegates to `ChatMessageCellDelegate`, which is implemented by `ChatActivity`.

## Architecture & Detailed Changes

### 1. Action IDs (`TMessagesProj/src/main/res/values/ids.xml`)
Declare new IDs in `ids.xml`:
- `acc_action_copy`
- `acc_action_reply`
- `acc_action_forward`
- `acc_action_open_reactions`
- `acc_action_edit`

Reuse existing IDs:
- `acc_action_delete`
- `acc_action_pin`
- `acc_action_unpin`
- `acc_action_toggle_selection`

### 2. Cell Delegate Contract (`TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java`)
Add delegate methods to `ChatMessageCell.ChatMessageCellDelegate`:
```java
default void addAccessibilityActionsForCell(ChatMessageCell cell, AccessibilityNodeInfo info) {
}

default boolean performAccessibilityActionForCell(ChatMessageCell cell, int action) {
    return false;
}
```

### 3. Cell Implementation (`ChatMessageCell.java`)
In `ChatMessageCell`:
- In `onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info)`:
  - If `delegate != null`, call `delegate.addAccessibilityActionsForCell(this, info)`.
  - If the parent chat is in action mode, set `info.setCheckable(true)` and `info.setChecked(delegate.isMessageSelected(message.getId()))`.
- In `performAccessibilityAction(int action, Bundle arguments)`:
  - Prioritize delegate handling:
    ```java
    if (delegate != null && delegate.performAccessibilityActionForCell(this, action)) {
        return true;
    }
    ```

### 4. Activity Integration (`TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java`)
In `ChatActivity`:

#### Helper Methods
- `boolean isMessageSelected(int id)`: returns whether `selectedMessagesIds[0].indexOfKey(id) >= 0 || selectedMessagesIds[1].indexOfKey(id) >= 0`.
- `void addAccessibilityActionsForCell(ChatMessageCell cell, AccessibilityNodeInfo info)`:
  - Validates `cell` and `cell.getMessageObject()`.
  - Checks if `actionBar != null && actionBar.isActionModeShowed()`.
  - If in action mode:
    - Adds `acc_action_toggle_selection` with label `R.string.Deselect` or `R.string.Select`.
    - If message is selected and selected count > 1, populates bulk actions:
      - `acc_action_delete` if `cantDeleteMessagesCount == 0`.
      - `acc_action_forward` if `cantForwardMessagesCount == 0`.
      - `acc_action_copy` if all selected messages can be copied.
      - Returns early.
  - If not in action mode:
    - Adds `acc_action_toggle_selection` with label `R.string.AccActionEnterSelectionMode`.
  - Evaluates message permissions mirroring `fillMessageMenu`:
    - If `canPerformReply()` and message allows reply: adds `acc_action_reply`.
    - If copyable: adds `acc_action_copy`.
    - If `canForward`: adds `acc_action_forward`.
    - If reactions available: adds `acc_action_open_reactions` with label `R.string.Reactions`.
    - If `canEditMessage`: adds `acc_action_edit`.
    - If pinning allowed: adds `acc_action_unpin` (if pinned) or `acc_action_pin` (if unpinned).
    - If `canDeleteMessage`: adds `acc_action_delete`.

- `boolean performAccessibilityActionForCell(ChatMessageCell cell, int action)`:
  - Validates cell and message.
  - Handles `acc_action_toggle_selection`:
    - If not in action mode: starts multiselect and selects message.
    - If in action mode: toggles selection via `addToSelectedMessages(message, true)`.
  - Handles bulk actions if message is selected and multi-selected:
    - `acc_action_delete`: shows delete alert for selection.
    - `acc_action_forward`: launches forward flow for selection.
    - `acc_action_copy`: copies selected messages to clipboard.
  - Handles single actions:
    - `acc_action_copy`: copies content to clipboard and shows UndoView bulletin.
    - `acc_action_reply`: invokes `showFieldPanelForReply(message)`.
    - `acc_action_forward`: launches forward dialog for message.
    - `acc_action_open_reactions`: opens expanded reactions window for message.
    - `acc_action_edit`: enters edit mode for message.
    - `acc_action_pin` / `acc_action_unpin`: toggles pin state.
    - `acc_action_delete`: shows delete alert for message.
  - Returns `true` if handled.

## Testing & Verification Plan
- Build project using Gradle to ensure clean compilation.
- Verify XML action IDs resolve properly without collisions.
- Verify accessibility node hierarchy and action strings in TalkBack exploration mode.
- Verify selection state announcements and bulk action execution.
