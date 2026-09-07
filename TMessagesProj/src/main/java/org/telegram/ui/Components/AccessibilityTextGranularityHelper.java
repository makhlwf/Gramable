package org.telegram.ui.Components;

import android.content.Context;
import android.os.Bundle;
import android.text.Layout;
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import org.telegram.messenger.FileLog;

import java.text.BreakIterator;
import java.util.Locale;

public class AccessibilityTextGranularityHelper {

    private static final int ACCESSIBILITY_CURSOR_POSITION_UNDEFINED = -1;

    private int cursorPosition = ACCESSIBILITY_CURSOR_POSITION_UNDEFINED;
    private int selectionStart = ACCESSIBILITY_CURSOR_POSITION_UNDEFINED;
    private int selectionEnd = ACCESSIBILITY_CURSOR_POSITION_UNDEFINED;

    private CharacterTextSegmentIterator characterIterator;
    private WordTextSegmentIterator wordIterator;
    private ParagraphTextSegmentIterator paragraphIterator;
    private LineTextSegmentIterator lineIterator;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int start, int end);
    }

    private OnSelectionChangedListener onSelectionChangedListener;
    private boolean suppressListener;

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.onSelectionChangedListener = listener;
    }

    public void setSelectionRange(int start, int end) {
        this.selectionStart = start;
        this.selectionEnd = end;
        this.cursorPosition = end;
    }

    public void setSelectionRange(View hostView, CharSequence text, int start, int end) {
        if (selectionStart == start && selectionEnd == end && cursorPosition == end) {
            return;
        }
        suppressListener = true;
        try {
            setSelection(hostView, text, start, end);
        } finally {
            suppressListener = false;
        }
    }

    public int getSelectionStart() {
        return selectionStart;
    }

    public int getSelectionEnd() {
        return selectionEnd;
    }

    public void reset() {
        cursorPosition = ACCESSIBILITY_CURSOR_POSITION_UNDEFINED;
        selectionStart = ACCESSIBILITY_CURSOR_POSITION_UNDEFINED;
        selectionEnd = ACCESSIBILITY_CURSOR_POSITION_UNDEFINED;
    }

    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info, CharSequence text) {
        if (info == null) {
            return;
        }
        if (!TextUtils.isEmpty(text)) {
            info.addAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
            info.addAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY);
            info.addAction(AccessibilityNodeInfo.ACTION_SET_SELECTION);
            info.setMovementGranularities(
                    AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER
                    | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD
                    | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE
                    | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH
            );
            if (selectionStart >= 0 && selectionEnd >= 0) {
                info.setTextSelection(selectionStart, selectionEnd);
            } else if (cursorPosition >= 0) {
                info.setTextSelection(cursorPosition, cursorPosition);
            } else {
                info.setTextSelection(-1, -1);
            }
        }
    }

    public boolean performAccessibilityAction(View hostView, CharSequence text, Layout layout, int action, Bundle arguments) {
        if (hostView == null) {
            return false;
        }
        if (action == AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY) {
            int granularity = arguments != null ? arguments.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT) : 0;
            boolean extendSelection = arguments != null && arguments.getBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN);
            return traverseAtGranularity(hostView, text, layout, granularity, true, extendSelection);
        } else if (action == AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY) {
            int granularity = arguments != null ? arguments.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT) : 0;
            boolean extendSelection = arguments != null && arguments.getBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN);
            return traverseAtGranularity(hostView, text, layout, granularity, false, extendSelection);
        } else if (action == AccessibilityNodeInfo.ACTION_SET_SELECTION) {
            if (text == null) {
                return false;
            }
            int start = arguments != null ? arguments.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, -1) : -1;
            int end = arguments != null ? arguments.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, -1) : -1;
            if (start >= 0 && start <= text.length() && end >= 0 && end <= text.length()) {
                setSelection(hostView, text, start, end);
                return true;
            }
            return false;
        } else if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS || action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
            reset();
            return false;
        }
        return false;
    }

    private boolean traverseAtGranularity(View hostView, CharSequence text, Layout layout, int granularity, boolean forward, boolean extendSelection) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        int textLength = text.length();
        int current = cursorPosition;
        if (current == ACCESSIBILITY_CURSOR_POSITION_UNDEFINED) {
            current = forward ? 0 : textLength;
        }

        int[] range = null;
        switch (granularity) {
            case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER:
                range = getCharacterRange(text, current, forward);
                break;
            case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD:
                range = getWordRange(text, current, forward);
                break;
            case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE:
                range = getLineRange(text, layout, current, forward);
                break;
            case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH:
                range = getParagraphRange(text, current, forward);
                break;
        }

        if (range == null) {
            return false;
        }

        int segmentStart = range[0];
        int segmentEnd = range[1];

        int newSelectionStart;
        int newSelectionEnd;
        if (extendSelection) {
            if (selectionStart == ACCESSIBILITY_CURSOR_POSITION_UNDEFINED) {
                newSelectionStart = forward ? segmentStart : segmentEnd;
            } else {
                newSelectionStart = selectionStart;
            }
            newSelectionEnd = forward ? segmentEnd : segmentStart;
        } else {
            newSelectionStart = newSelectionEnd = forward ? segmentEnd : segmentStart;
        }

        setSelection(hostView, text, newSelectionStart, newSelectionEnd);

        int action = forward ? AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
                : AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY;
        sendTraversedEvent(hostView, text, action, granularity, segmentStart, segmentEnd);
        return true;
    }

    private void setSelection(View hostView, CharSequence text, int start, int end) {
        if (selectionStart == start && selectionEnd == end && cursorPosition == end) {
            return;
        }
        selectionStart = start;
        selectionEnd = end;
        cursorPosition = end;
        sendSelectionEvent(hostView, text, start, end);
        if (onSelectionChangedListener != null && !suppressListener) {
            onSelectionChangedListener.onSelectionChanged(start, end);
        }
    }

    private void sendSelectionEvent(View hostView, CharSequence text, int start, int end) {
        try {
            AccessibilityManager am = (AccessibilityManager) hostView.getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am == null || !am.isEnabled()) {
                return;
            }
            AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED);
            event.setPackageName(hostView.getContext().getPackageName());
            event.setClassName(hostView.getClass().getName());
            event.setSource(hostView);
            event.getText().add(text);
            event.setFromIndex(start);
            event.setToIndex(end);
            event.setItemCount(text.length());
            event.setEnabled(true);
            if (hostView.getParent() != null) {
                hostView.getParent().requestSendAccessibilityEvent(hostView, event);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void sendTraversedEvent(View hostView, CharSequence text, int action, int granularity, int fromIndex, int toIndex) {
        try {
            AccessibilityManager am = (AccessibilityManager) hostView.getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am == null || !am.isEnabled()) {
                return;
            }
            AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY);
            event.setPackageName(hostView.getContext().getPackageName());
            event.setClassName(hostView.getClass().getName());
            event.setSource(hostView);
            event.getText().add(text);
            event.setFromIndex(fromIndex);
            event.setToIndex(toIndex);
            event.setAction(action);
            event.setMovementGranularity(granularity);
            event.setEnabled(true);
            if (hostView.getParent() != null) {
                hostView.getParent().requestSendAccessibilityEvent(hostView, event);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private int[] getCharacterRange(CharSequence text, int offset, boolean forward) {
        if (characterIterator == null) {
            characterIterator = new CharacterTextSegmentIterator();
        }
        characterIterator.initialize(text.toString());
        return forward ? characterIterator.following(offset) : characterIterator.preceding(offset);
    }

    private int[] getWordRange(CharSequence text, int offset, boolean forward) {
        if (wordIterator == null) {
            wordIterator = new WordTextSegmentIterator();
        }
        wordIterator.initialize(text.toString());
        return forward ? wordIterator.following(offset) : wordIterator.preceding(offset);
    }

    private int[] getParagraphRange(CharSequence text, int offset, boolean forward) {
        if (paragraphIterator == null) {
            paragraphIterator = new ParagraphTextSegmentIterator();
        }
        paragraphIterator.initialize(text.toString());
        return forward ? paragraphIterator.following(offset) : paragraphIterator.preceding(offset);
    }

    private int[] getLineRange(CharSequence text, Layout layout, int offset, boolean forward) {
        if (lineIterator == null) {
            lineIterator = new LineTextSegmentIterator();
        }
        lineIterator.initialize(text.toString(), layout);
        return forward ? lineIterator.following(offset) : lineIterator.preceding(offset);
    }

    public static class CharacterTextSegmentIterator {
        private String text;
        private BreakIterator impl;
        private Locale locale;

        public void initialize(String text) {
            this.text = text;
            Locale currentLocale = Locale.getDefault();
            if (locale == null || !locale.equals(currentLocale) || impl == null) {
                locale = currentLocale;
                impl = BreakIterator.getCharacterInstance(locale);
            }
            try {
                impl.setText(text);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        public int[] following(int offset) {
            if (text == null || text.length() == 0 || offset >= text.length()) {
                return null;
            }
            try {
                int start = offset;
                if (start < 0) {
                    start = 0;
                }
                while (!impl.isBoundary(start)) {
                    start = impl.following(start);
                    if (start == BreakIterator.DONE) {
                        return null;
                    }
                }
                int end = impl.following(start);
                if (end == BreakIterator.DONE || start >= end) {
                    return null;
                }
                return new int[]{start, end};
            } catch (Exception e) {
                FileLog.e(e);
                return null;
            }
        }

        public int[] preceding(int offset) {
            if (text == null || text.length() == 0 || offset <= 0) {
                return null;
            }
            try {
                int end = offset;
                if (end > text.length()) {
                    end = text.length();
                }
                while (!impl.isBoundary(end)) {
                    end = impl.preceding(end);
                    if (end == BreakIterator.DONE) {
                        return null;
                    }
                }
                int start = impl.preceding(end);
                if (start == BreakIterator.DONE || start >= end) {
                    return null;
                }
                return new int[]{start, end};
            } catch (Exception e) {
                FileLog.e(e);
                return null;
            }
        }
    }

    public static class WordTextSegmentIterator {
        private String text;
        private BreakIterator impl;
        private Locale locale;

        public void initialize(String text) {
            this.text = text;
            Locale currentLocale = Locale.getDefault();
            if (locale == null || !locale.equals(currentLocale) || impl == null) {
                locale = currentLocale;
                impl = BreakIterator.getWordInstance(locale);
            }
            try {
                impl.setText(text);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        public int[] following(int offset) {
            if (text == null || text.length() == 0 || offset >= text.length()) {
                return null;
            }
            try {
                int start = offset;
                if (start < 0) {
                    start = 0;
                }
                while (start < text.length() && !isStartBoundary(start)) {
                    start = impl.following(start);
                    if (start == BreakIterator.DONE) {
                        return null;
                    }
                }
                if (start >= text.length()) {
                    return null;
                }
                int end = impl.following(start);
                while (end != BreakIterator.DONE && !isEndBoundary(end)) {
                    end = impl.following(end);
                }
                if (end == BreakIterator.DONE || start >= end) {
                    return null;
                }
                return new int[]{start, end};
            } catch (Exception e) {
                FileLog.e(e);
                return null;
            }
        }

        public int[] preceding(int offset) {
            if (text == null || text.length() == 0 || offset <= 0) {
                return null;
            }
            try {
                int end = offset;
                if (end > text.length()) {
                    end = text.length();
                }
                while (end > 0 && !isEndBoundary(end)) {
                    end = impl.preceding(end);
                    if (end == BreakIterator.DONE) {
                        return null;
                    }
                }
                if (end <= 0) {
                    return null;
                }
                int start = impl.preceding(end);
                while (start != BreakIterator.DONE && !isStartBoundary(start)) {
                    start = impl.preceding(start);
                }
                if (start == BreakIterator.DONE || start >= end) {
                    return null;
                }
                return new int[]{start, end};
            } catch (Exception e) {
                FileLog.e(e);
                return null;
            }
        }

        private boolean isStartBoundary(int index) {
            return isLetterOrDigit(index) && (index == 0 || !isLetterOrDigit(index - 1));
        }

        private boolean isEndBoundary(int index) {
            return index > 0 && isLetterOrDigit(index - 1) && (index == text.length() || !isLetterOrDigit(index));
        }

        private boolean isLetterOrDigit(int index) {
            if (index >= 0 && index < text.length()) {
                int codePoint = Character.codePointAt(text, index);
                if (Character.isLetterOrDigit(codePoint)) {
                    return true;
                }
                int type = Character.getType(codePoint);
                return type == Character.OTHER_SYMBOL
                        || type == Character.SURROGATE
                        || type == Character.MODIFIER_SYMBOL
                        || type == Character.MATH_SYMBOL;
            }
            return false;
        }
    }

    public static class ParagraphTextSegmentIterator {
        private String text;

        public void initialize(String text) {
            this.text = text;
        }

        public int[] following(int offset) {
            if (text == null || text.length() == 0 || offset >= text.length()) {
                return null;
            }
            try {
                int start = offset;
                if (start < 0) {
                    start = 0;
                }
                while (start < text.length() && text.charAt(start) == '\n' && !isStartBoundary(start)) {
                    start++;
                }
                if (start >= text.length()) {
                    return null;
                }
                int end = start + 1;
                while (end < text.length() && !isEndBoundary(end)) {
                    end++;
                }
                return new int[]{start, end};
            } catch (Exception e) {
                FileLog.e(e);
                return null;
            }
        }

        public int[] preceding(int offset) {
            if (text == null || text.length() == 0 || offset <= 0) {
                return null;
            }
            try {
                int end = offset;
                if (end > text.length()) {
                    end = text.length();
                }
                while (end > 0 && text.charAt(end - 1) == '\n' && !isEndBoundary(end)) {
                    end--;
                }
                if (end <= 0) {
                    return null;
                }
                int start = end - 1;
                while (start > 0 && !isStartBoundary(start)) {
                    start--;
                }
                return new int[]{start, end};
            } catch (Exception e) {
                FileLog.e(e);
                return null;
            }
        }

        private boolean isStartBoundary(int index) {
            return text.charAt(index) != '\n' && (index == 0 || text.charAt(index - 1) == '\n');
        }

        private boolean isEndBoundary(int index) {
            return index > 0 && text.charAt(index - 1) != '\n' && (index == text.length() || text.charAt(index) == '\n');
        }
    }

    public static class LineTextSegmentIterator {
        private String text;
        private Layout layout;

        public void initialize(String text, Layout layout) {
            this.text = text;
            this.layout = layout;
        }

        public int[] following(int offset) {
            if (text == null || text.length() == 0 || offset >= text.length()) {
                return null;
            }
            if (layout == null) {
                return fallbackFollowing(offset);
            }
            try {
                int nextLine;
                if (offset < 0) {
                    nextLine = layout.getLineForOffset(0);
                } else {
                    int currentLine = layout.getLineForOffset(offset);
                    if (layout.getLineStart(currentLine) == offset) {
                        nextLine = currentLine;
                    } else {
                        nextLine = currentLine + 1;
                    }
                }
                while (nextLine < layout.getLineCount()) {
                    int start = layout.getLineStart(nextLine);
                    int end = layout.getLineEnd(nextLine);
                    if (start < end) {
                        return new int[]{start, end};
                    }
                    nextLine++;
                }
                return null;
            } catch (Exception e) {
                FileLog.e(e);
                return fallbackFollowing(offset);
            }
        }

        public int[] preceding(int offset) {
            if (text == null || text.length() == 0 || offset <= 0) {
                return null;
            }
            if (layout == null) {
                return fallbackPreceding(offset);
            }
            try {
                int previousLine;
                if (offset > text.length()) {
                    previousLine = layout.getLineForOffset(text.length());
                } else {
                    int currentLine = layout.getLineForOffset(offset);
                    if (layout.getLineEnd(currentLine) == offset) {
                        previousLine = currentLine;
                    } else {
                        previousLine = currentLine - 1;
                    }
                }
                while (previousLine >= 0) {
                    int start = layout.getLineStart(previousLine);
                    int end = layout.getLineEnd(previousLine);
                    if (start < end) {
                        return new int[]{start, end};
                    }
                    previousLine--;
                }
                return null;
            } catch (Exception e) {
                FileLog.e(e);
                return fallbackPreceding(offset);
            }
        }

        private int[] fallbackFollowing(int offset) {
            int start = Math.max(0, offset);
            if (start >= text.length()) {
                return null;
            }
            int end = text.indexOf('\n', start);
            if (end == -1) {
                end = text.length();
            } else {
                end++;
            }
            return start < end ? new int[]{start, end} : null;
        }

        private int[] fallbackPreceding(int offset) {
            int end = Math.min(text.length(), offset);
            if (end <= 0) {
                return null;
            }
            int searchPos = end - 1;
            if (searchPos > 0 && text.charAt(searchPos) == '\n') {
                searchPos--;
            }
            int prevNewline = text.lastIndexOf('\n', searchPos);
            int start = prevNewline == -1 ? 0 : prevNewline + 1;
            return start < end ? new int[]{start, end} : null;
        }
    }
}
