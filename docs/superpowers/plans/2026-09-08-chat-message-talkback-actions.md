# TalkBack Accessibility Actions for Chat Messages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement TalkBack custom accessibility actions on chat message bubbles with full feature parity to the dialogues chat list, supporting copy, reply, forward, reactions panel, edit, pin/unpin, delete, and multiselect/bulk actions.

**Architecture:** Extend `ChatMessageCellDelegate` with accessibility callback methods (`addAccessibilityActionsForCell`, `performAccessibilityActionForCell`, `isMessageSelected`), invoke them in `ChatMessageCell` during node initialization and action dispatch, and implement the business logic in `ChatActivity`.

**Tech Stack:** Android Java, AccessibilityNodeInfo, TalkBack, Telegram UI components.

## Global Constraints
- Strictly follow the delegate pattern established in `DialogCell` and `DialogsActivity`.
- Use existing strings from `R.string` (`Copy`, `Reply`, `Forward`, `Reactions`, `Edit`, `PinMessage`, `UnpinMessage`, `Delete`, `Remove`, `Select`, `Deselect`, `AccActionEnterSelectionMode`).
- Preserve all existing comments and docstrings.

---

### Task 1: Declare Action IDs in `ids.xml`

**Files:**
- Modify: `TMessagesProj/src/main/res/values/ids.xml:45-55`

**Interfaces:**
- Produces: Action resource IDs: `R.id.acc_action_copy`, `R.id.acc_action_reply`, `R.id.acc_action_forward`, `R.id.acc_action_open_reactions`, `R.id.acc_action_edit`.

- [ ] **Step 1: Declare action IDs in ids.xml**
Add the new action IDs to `TMessagesProj/src/main/res/values/ids.xml` alongside existing `acc_action_...` IDs.

```xml
    <item name="acc_action_copy" type="id"/>
    <item name="acc_action_reply" type="id"/>
    <item name="acc_action_forward" type="id"/>
    <item name="acc_action_open_reactions" type="id"/>
    <item name="acc_action_edit" type="id"/>
```

- [ ] **Step 2: Verify IDs declaration**
Inspect `TMessagesProj/src/main/res/values/ids.xml` to ensure all 5 IDs are declared with correct syntax and no duplicates.

- [ ] **Step 3: Commit**
```bash
git add TMessagesProj/src/main/res/values/ids.xml
git commit -m "feat(a11y): declare chat message accessibility action IDs"
```

---

### Task 2: Make `showCustomEmojiReactionDialog` accessible in `ReactionsContainerLayout`

**Files:**
- Modify: `TMessagesProj/src/main/java/org/telegram/ui/Components/ReactionsContainerLayout.java:506`

**Interfaces:**
- Produces: `public void showCustomEmojiReactionDialog()` on `ReactionsContainerLayout`.

- [ ] **Step 1: Expose `showCustomEmojiReactionDialog`**
Change `private void showCustomEmojiReactionDialog()` to `public void showCustomEmojiReactionDialog()` in `ReactionsContainerLayout.java` so that it can be triggered programmatically from `ChatActivity`.

- [ ] **Step 2: Verify visibility change**
Confirm that `showCustomEmojiReactionDialog()` compiles and is callable from `ChatActivity`.

- [ ] **Step 3: Commit**
```bash
git add TMessagesProj/src/main/java/org/telegram/ui/Components/ReactionsContainerLayout.java
git commit -m "feat(a11y): expose showCustomEmojiReactionDialog on ReactionsContainerLayout"
```

---

### Task 3: Update `ChatMessageCellDelegate` and `ChatMessageCell`

**Files:**
- Modify: `TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java`

**Interfaces:**
- Consumes: Action IDs from `ids.xml`.
- Produces: Delegate methods in `ChatMessageCell.ChatMessageCellDelegate`:
  - `default void addAccessibilityActionsForCell(ChatMessageCell cell, AccessibilityNodeInfo info) {}`
  - `default boolean performAccessibilityActionForCell(ChatMessageCell cell, int action) { return false; }`
  - `default boolean isMessageSelected(int id) { return false; }`

- [ ] **Step 1: Add delegate methods to `ChatMessageCell.ChatMessageCellDelegate`**
In `ChatMessageCell.java` around line 560, declare the three default methods in `ChatMessageCellDelegate`.

- [ ] **Step 2: Update `onInitializeAccessibilityNodeInfo` in `ChatMessageCell`**
In `ChatMessageCell.java` around line 26725, delegate action addition and expose selection state:
```java
        if (delegate != null) {
            delegate.addAccessibilityActionsForCell(this, info);
            if (currentMessageObject != null && delegate.hasSelectedMessages()) {
                info.setCheckable(true);
                info.setChecked(delegate.isMessageSelected(currentMessageObject.getId()));
            }
        }
```

- [ ] **Step 3: Prioritize delegate in `performAccessibilityAction` in `ChatMessageCell`**
In `ChatMessageCell.java` around line 26625, check delegate before default handling:
```java
    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (delegate != null && delegate.performAccessibilityActionForCell(this, action)) {
            return true;
        }
        if (delegate != null && delegate.onAccessibilityAction(action, arguments)) {
            return false;
        }
        ...
```

- [ ] **Step 4: Commit**
```bash
git add TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java
git commit -m "feat(a11y): delegate accessibility actions and expose selection state in ChatMessageCell"
```

---

### Task 4: Implement Accessibility Actions Population & Execution in `ChatActivity`

**Files:**
- Modify: `TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java`

**Interfaces:**
- Consumes: `ChatMessageCellDelegate`, action IDs from `ids.xml`, `ReactionsContainerLayout.showCustomEmojiReactionDialog`.
- Produces:
  - `public boolean isMessageSelected(int id)`
  - `public void addAccessibilityActionsForCell(ChatMessageCell cell, AccessibilityNodeInfo info)`
  - `public boolean performAccessibilityActionForCell(ChatMessageCell cell, int action)`
  - Implementation of these methods inside `ChatActivity.ChatMessageCellDelegate`.

- [ ] **Step 1: Store `currentReactionsLayout` in `ChatActivity`**
In `ChatActivity.java` around line 32097 where `reactionsLayout` is instantiated in `createMenu`, assign it to `currentReactionsLayout = reactionsLayout;` and reset it to `null` in `closeMenu()`.

- [ ] **Step 2: Implement `isMessageSelected`, `addAccessibilityActionsForCell`, and `populateBulkMessageAccessibilityActions`**
Add helper methods in `ChatActivity.java`:
- `isMessageSelected(int id)`: checks `selectedMessagesIds[0]` and `selectedMessagesIds[1]`.
- `populateBulkMessageAccessibilityActions(AccessibilityNodeInfo info)`: attaches bulk delete, forward, and copy actions when multiple messages are selected.
- `addAccessibilityActionsForCell(ChatMessageCell cell, AccessibilityNodeInfo info)`: dynamically checks message permissions and state to attach `acc_action_toggle_selection`, `acc_action_reply`, `acc_action_copy`, `acc_action_forward`, `acc_action_open_reactions`, `acc_action_edit`, `acc_action_pin`/`acc_action_unpin`, and `acc_action_delete`.

- [ ] **Step 3: Implement `performAccessibilityActionForCell` in `ChatActivity`**
Implement action handling in `ChatActivity.java`:
- Selection toggle (`acc_action_toggle_selection`)
- Bulk delete, forward, copy (when multi-selected)
- Single message actions:
  - Copy (`acc_action_copy`)
  - Reply (`acc_action_reply`)
  - Forward (`acc_action_forward`)
  - Reactions (`acc_action_open_reactions` calling `openReactionsWindowForMessage(cell, message)`)
  - Edit (`acc_action_edit`)
  - Pin/Unpin (`acc_action_pin`, `acc_action_unpin`)
  - Delete (`acc_action_delete`)

- [ ] **Step 4: Bridge `ChatMessageCellDelegate` inner class in `ChatActivity`**
In `ChatActivity.ChatMessageCellDelegate` (around line 38916), forward:
- `addAccessibilityActionsForCell(cell, info)` -> `ChatActivity.this.addAccessibilityActionsForCell(cell, info)`
- `performAccessibilityActionForCell(cell, action)` -> `ChatActivity.this.performAccessibilityActionForCell(cell, action)`
- `isMessageSelected(id)` -> `ChatActivity.this.isMessageSelected(id)`

- [ ] **Step 5: Commit**
```bash
git add TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java
git commit -m "feat(a11y): implement message accessibility actions logic in ChatActivity"
```

---

### Task 5: Compilation and Verification

**Files:**
- Verification only

- [ ] **Step 1: Run static analysis / compilation**
Run `./gradlew assembleAfatDebug` or `gradlew compileDebugJavaWithJavac` to verify that there are no compilation errors, missing symbols, or type mismatches.

- [ ] **Step 2: Inspect git diff**
Run `git diff HEAD~4` to review the entire patch across all files and verify clean code formatting and comments preservation.
