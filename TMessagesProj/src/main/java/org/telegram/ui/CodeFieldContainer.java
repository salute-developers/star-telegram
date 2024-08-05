package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import ru.sberdevices.sbdv.util.TelegramDimensions;

public class CodeFieldContainer extends LinearLayout {
    public final static int TYPE_PASSCODE = 10;

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    float strokeWidth;
    public boolean ignoreOnTextChange;
    public boolean isFocusSuppressed;

    public CodeNumberField[] codeField;

    public CodeFieldContainer(Context context) {
        super(context);
        paint.setStyle(Paint.Style.STROKE);
        setOrientation(HORIZONTAL);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        paint.setStrokeWidth(strokeWidth = AndroidUtilities.dp(1.5f));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof CodeNumberField) {
                CodeNumberField codeField = (CodeNumberField) child;
                if (!isFocusSuppressed) {
                    if (child.isFocused()) {
                        codeField.animateFocusedProgress(1f);
                    } else if (!child.isFocused()) {
                        codeField.animateFocusedProgress(0);
                    }
                }
                float successProgress = codeField.getSuccessProgress();
                int focusClr = ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteInputField), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), codeField.getFocusedProgress());
                int errorClr = ColorUtils.blendARGB(focusClr, Theme.getColor(Theme.key_text_RedBold), codeField.getErrorProgress());
                paint.setColor(ColorUtils.blendARGB(errorClr, Theme.getColor(Theme.key_checkbox), successProgress));
                AndroidUtilities.rectTmp.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
                AndroidUtilities.rectTmp.inset(strokeWidth, strokeWidth);
                if (successProgress != 0) {
                    float offset = -Math.max(0, strokeWidth * (codeField.getSuccessScaleProgress() - 1f));
                    AndroidUtilities.rectTmp.inset(offset, offset);
                }

                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
            }
        }
        super.dispatchDraw(canvas);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child instanceof CodeNumberField) {
            CodeNumberField field = (CodeNumberField) child;
            canvas.save();
            float progress = ((CodeNumberField) child).enterAnimation;
            AndroidUtilities.rectTmp.set(child.getX(), child.getY(), child.getX() + child.getMeasuredWidth(), child.getY() + child.getMeasuredHeight());
            AndroidUtilities.rectTmp.inset(strokeWidth, strokeWidth);
            canvas.clipRect(AndroidUtilities.rectTmp);
            if (field.replaceAnimation) {
                float s = progress * 0.5f + 0.5f;
                child.setAlpha(progress);
                canvas.scale(s, s, field.getX() + field.getMeasuredWidth() / 2f, field.getY() + field.getMeasuredHeight() / 2f);
            } else {
                child.setAlpha(1f);
                canvas.translate(0, child.getMeasuredHeight() * (1f - progress));
            }
            super.drawChild(canvas, child, drawingTime);
            canvas.restore();

            float exitProgress = field.exitAnimation;
            if (exitProgress < 1f) {
                canvas.save();
                float s = (1f - exitProgress) * 0.5f + 0.5f;
                canvas.scale(s, s, field.getX() + field.getMeasuredWidth() / 2f, field.getY() + field.getMeasuredHeight() / 2f);
                bitmapPaint.setAlpha((int) (255 * (1f - exitProgress)));
                canvas.drawBitmap(field.exitBitmap, field.getX(), field.getY(), bitmapPaint);
                canvas.restore();
            }
            return true;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    public void setNumbersCount(int length, int currentType) {
        if (codeField == null || codeField.length != length) {
            if (codeField != null) {
                for (CodeNumberField f : codeField) {
                    removeView(f);
                }
            }
            codeField = new CodeNumberField[length];
            for (int a = 0; a < length; a++) {
                final int num = a;
                codeField[a] = new CodeNumberField(getContext());
                codeField[a].setTextColor(getContext().getColor(R.color.white));
                codeField[a].setCursorColor(getContext().getColor(R.color.transparent));
                codeField[a].setCursorSize(AndroidUtilities.dp(TelegramDimensions.getPhoneLoginEditTextButtonTextSize()));
                codeField[a].setCursorWidth(1.5f);
                codeField[a].setNextFocusUpId(R.id.actionbar_back_btn);
                codeField[a].setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        Drawable pressedDrawable = v.getBackground().mutate();
                        pressedDrawable.setColorFilter(new PorterDuffColorFilter(getContext().getColor(R.color.sbdv_green_light), PorterDuff.Mode.MULTIPLY));
                        v.setBackgroundDrawable(pressedDrawable);
                    } else {
                        Drawable pressedDrawable = v.getBackground().mutate();
                        pressedDrawable.setColorFilter(new PorterDuffColorFilter(getContext().getColor(R.color.sbdv_white_28), PorterDuff.Mode.MULTIPLY));
                        v.setBackgroundDrawable(pressedDrawable);
                    }
                });

                Drawable pressedDrawable = getResources().getDrawable(R.drawable.search_dark_activated).mutate();
                pressedDrawable.setColorFilter(new PorterDuffColorFilter(getContext().getColor(R.color.sbdv_white_28), PorterDuff.Mode.MULTIPLY));

                codeField[a].setBackgroundDrawable(pressedDrawable);
                codeField[a].setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                codeField[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, TelegramDimensions.getPhoneLoginEditTextButtonTextSize());
                codeField[a].setMaxLines(1);
                codeField[a].setPadding(0, 0, 0, 0);
                codeField[a].setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
                if (currentType == 3) {
                    codeField[a].setEnabled(false);
                    codeField[a].setInputType(InputType.TYPE_NULL);
                    codeField[a].setVisibility(GONE);
                } else {
                    codeField[a].setInputType(InputType.TYPE_CLASS_PHONE);
                }
                int width;
                int height;
                int gapSize;
                if (currentType == TYPE_PASSCODE) {
                    width = 42;
                    height = 47;
                    gapSize = 10;
                } else if (currentType == LoginActivity.AUTH_TYPE_MISSED_CALL) {
                    width = 28;
                    height = 34;
                    gapSize = 5;
                } else {
                    width = 34;
                    height = 42;
                    gapSize = 7;
                }
                addView(codeField[a], LayoutHelper.createLinear(width, height, Gravity.CENTER_HORIZONTAL, 0, 0, a != length - 1 ? gapSize: 0, 0));
                codeField[a].addTextChangedListener(new TextWatcher() {

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (ignoreOnTextChange) {
                            return;
                        }
                        int len = s.length();
                        if (len >= 1) {
                            int n = num;
                            if (len > 1) {
                                String text = s.toString();
                                ignoreOnTextChange = true;
                                for (int a = 0; a < Math.min(length - num, len); a++) {
                                    if (a == 0) {
                                        s.replace(0, len, text.substring(a, a + 1));
                                    } else {
                                        n++;
                                        if (num + a < codeField.length) {
                                            codeField[num + a].setText(text.substring(a, a + 1));
                                        }
                                    }
                                }
                                ignoreOnTextChange = false;
                            }


                            if (n + 1 >= 0 && n + 1 < codeField.length) {
                                codeField[n + 1].setSelection(codeField[n + 1].length());
                                codeField[n + 1].requestFocus();
                            }
                            if ((num == length - 1 || num == length - 2 && len >= 2) && getCode().length() == length) {
                                processNextPressed();
                            }
                        }
                    }
                });
                codeField[a].setOnKeyListener((v, keyCode, event) -> {
                    if (keyCode == KeyEvent.KEYCODE_DEL && codeField[num].length() == 0 && num > 0) {
                        codeField[num - 1].setSelection(codeField[num - 1].length());
                        codeField[num - 1].requestFocus();
                        codeField[num - 1].dispatchKeyEvent(event);
                        return true;
                    }
                    return false;
                });
                codeField[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                    if (i == EditorInfo.IME_ACTION_NEXT) {
                        processNextPressed();
                        return true;
                    }
                    return false;
                });
            }
        } else {
            for (int a = 0; a < codeField.length; a++) {
                codeField[a].setText("");
            }
        }
    }

    protected void processNextPressed() {

    }

    public String getCode() {
        if (codeField == null) {
            return "";
        }
        StringBuilder codeBuilder = new StringBuilder();
        for (int a = 0; a < codeField.length; a++) {
            codeBuilder.append(PhoneFormat.stripExceptNumbers(codeField[a].getText().toString()));
        }
        return codeBuilder.toString();
    }

    public void setCode(String savedCode) {
        codeField[0].setText(savedCode);
    }

    public void setText(String code) {
        setText(code, false);
    }

    public void setText(String code, boolean fromPaste) {
        if (codeField == null) {
            return;
        }
        int startFrom = 0;
        if (fromPaste) {
            for (int i = 0; i < codeField.length; i++) {
                if (codeField[i].isFocused()) {
                    startFrom = i;
                    break;
                }
            }
        }
        for (int i = startFrom; i < Math.min(codeField.length, startFrom + code.length()); i++) {
            codeField[i].setText(Character.toString(code.charAt(i - startFrom)));
        }
    }

}
