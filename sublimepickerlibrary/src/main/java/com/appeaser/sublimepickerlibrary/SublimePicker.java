/*
 * Copyright 2016 Vikram Kakkar
 * Edited by wahidnr
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.appeaser.sublimepickerlibrary;

import android.animation.LayoutTransition;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.appeaser.sublimepickerlibrary.common.ButtonHandler;
import com.appeaser.sublimepickerlibrary.datepicker.SelectedDate;
import com.appeaser.sublimepickerlibrary.datepicker.SublimeDatePicker;
import com.appeaser.sublimepickerlibrary.helpers.SublimeListenerAdapter;
import com.appeaser.sublimepickerlibrary.helpers.SublimeOptions;
import com.appeaser.sublimepickerlibrary.timepicker.SublimeTimePicker;
import com.appeaser.sublimepickerlibrary.utilities.SUtils;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A customizable view that provisions picking of a date,
 * time and recurrence option, all from a single user-interface.
 * You can also view 'SublimePicker' as a collection of
 * material-styled (API 23) DatePicker, TimePicker
 * and RecurrencePicker, backported to API 14.
 * You can opt for any combination of these three Pickers.
 * <p>
 * Edited by wahidnr
 */
public class SublimePicker extends FrameLayout
        implements SublimeDatePicker.OnDateChangedListener,
        SublimeDatePicker.DatePickerValidationCallback,
        SublimeTimePicker.TimePickerValidationCallback {

    // Used for formatting date range
    private static final long MONTH_IN_MILLIS = DateUtils.YEAR_IN_MILLIS / 12;

    // Container for 'SublimeDatePicker' & 'SublimeTimePicker'
    private LinearLayout llMainContentHolder;

    // Keeps track which picker is showing
    private SublimeOptions.Picker mCurrentPicker, mHiddenPicker;

    // Date picker
    private SublimeDatePicker mDatePicker;

    // Time picker
    private SublimeTimePicker mTimePicker;

    // Callback
    private SublimeListenerAdapter mListener;

    // Client-set options
    private SublimeOptions mOptions;

    // Ok, cancel & switch button handler
    private ButtonHandler mButtonLayout;

    // Flags set based on client-set options {SublimeOptions}
    private boolean mDatePickerValid = true, mTimePickerValid = true,
            mDatePickerEnabled, mTimePickerEnabled,
            mDatePickerSyncStateCalled;

    // Used if listener returns
    // null/invalid(zero-length, empty) string
    private DateFormat mDefaultDateFormatter, mDefaultTimeFormatter;

    // Handle ok, cancel & switch button click events
    private final ButtonHandler.Callback mButtonLayoutCallback = new ButtonHandler.Callback() {
        @Override
        public void onOkay() {
            SelectedDate selectedDate = null;

            if (mDatePickerEnabled) {
                selectedDate = mDatePicker.getSelectedDate();
            }

            int hour = -1, minute = -1;

            if (mTimePickerEnabled) {
                hour = mTimePicker.getCurrentHour();
                minute = mTimePicker.getCurrentMinute();
            }

            mListener.onDateTimeRecurrenceSet(SublimePicker.this,
                    // DatePicker
                    selectedDate,
                    // TimePicker
                    hour, minute);
        }

        @Override
        public void onCancel() {
            mListener.onCancelled();
        }

        @Override
        public void onNeutral() {
            mListener.onNeutralButtonClick();
        }

        @Override
        public void onSwitch() {
            mCurrentPicker = mCurrentPicker == SublimeOptions.Picker.DATE_PICKER ?
                    SublimeOptions.Picker.TIME_PICKER
                    : SublimeOptions.Picker.DATE_PICKER;

            updateDisplay();
        }
    };

    public SublimePicker(Context context) {
        this(context, null);
    }

    public SublimePicker(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.sublimePickerStyle);
    }

    public SublimePicker(Context context, AttributeSet attrs, int defStyleAttr) {
        super(createThemeWrapper(context), attrs, defStyleAttr);
        initializeLayout();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SublimePicker(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(createThemeWrapper(context), attrs, defStyleAttr, defStyleRes);
        initializeLayout();
    }

    private static ContextThemeWrapper createThemeWrapper(Context context) {
        final TypedArray forParent = context.obtainStyledAttributes(
                new int[]{R.attr.sublimePickerStyle});
        int parentStyle = forParent.getResourceId(0, R.style.SublimePickerStyleLight);
        forParent.recycle();

        return new ContextThemeWrapper(context, parentStyle);
    }

    private void initializeLayout() {
        Context context = getContext();
        SUtils.initializeResources(context);

        LayoutInflater.from(context).inflate(R.layout.sublime_picker_view_layout,
                this, true);

        mDefaultDateFormatter = DateFormat.getDateInstance(DateFormat.MEDIUM,
                Locale.getDefault());
        mDefaultTimeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT,
                Locale.getDefault());
        mDefaultTimeFormatter.setTimeZone(TimeZone.getTimeZone("GMT+0"));

        llMainContentHolder = (LinearLayout) findViewById(R.id.llMainContentHolder);
        mButtonLayout = new ButtonHandler(this);

        mDatePicker = (SublimeDatePicker) findViewById(R.id.datePicker);
        mTimePicker = (SublimeTimePicker) findViewById(R.id.timePicker);
    }

    public void initializePicker(SublimeOptions options, SublimeListenerAdapter listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null.");
        }

        if (options != null) {
            options.verifyValidity();
        } else {
            options = new SublimeOptions();
        }

        mOptions = options;
        mListener = listener;

        processOptions();
        updateDisplay();
    }

    // Called before 'RecurrencePicker' is shown
    private void updateHiddenPicker() {
        if (mDatePickerEnabled && mTimePickerEnabled) {
            mHiddenPicker = mDatePicker.getVisibility() == View.VISIBLE ?
                    SublimeOptions.Picker.DATE_PICKER : SublimeOptions.Picker.TIME_PICKER;
        } else if (mDatePickerEnabled) {
            mHiddenPicker = SublimeOptions.Picker.DATE_PICKER;
        } else if (mTimePickerEnabled) {
            mHiddenPicker = SublimeOptions.Picker.TIME_PICKER;
        } else {
            mHiddenPicker = SublimeOptions.Picker.INVALID;
        }
    }

    // 'mHiddenPicker' retains the Picker that was active
    // before 'RecurrencePicker' was shown. On its dismissal,
    // we have an option to show either 'DatePicker' or 'TimePicker'.
    // 'mHiddenPicker' helps identify the correct option.
    private void updateCurrentPicker() {
        if (mHiddenPicker != SublimeOptions.Picker.INVALID) {
            mCurrentPicker = mHiddenPicker;
        } else {
            throw new RuntimeException("Logic issue: No valid option for mCurrentPicker");
        }
    }

    private void updateDisplay() {
        CharSequence switchButtonText;

        if (mCurrentPicker == SublimeOptions.Picker.DATE_PICKER) {

            if (mTimePickerEnabled) {
                mTimePicker.setVisibility(View.GONE);
            }

            mDatePicker.setVisibility(View.VISIBLE);
            llMainContentHolder.setVisibility(View.VISIBLE);

            if (mButtonLayout.isSwitcherButtonEnabled()) {
                Date toFormat = new Date(mTimePicker.getCurrentHour() * DateUtils.HOUR_IN_MILLIS
                        + mTimePicker.getCurrentMinute() * DateUtils.MINUTE_IN_MILLIS);

                switchButtonText = mListener.formatTime(toFormat);

                if (TextUtils.isEmpty(switchButtonText)) {
                    switchButtonText = mDefaultTimeFormatter.format(toFormat);
                }

                mButtonLayout.updateSwitcherText(SublimeOptions.Picker.DATE_PICKER, switchButtonText);
            }

            if (!mDatePickerSyncStateCalled) {
                mDatePickerSyncStateCalled = true;
            }
        } else if (mCurrentPicker == SublimeOptions.Picker.TIME_PICKER) {
            if (mDatePickerEnabled) {
                mDatePicker.setVisibility(View.GONE);
            }

            mTimePicker.setVisibility(View.VISIBLE);
            llMainContentHolder.setVisibility(View.VISIBLE);

            if (mButtonLayout.isSwitcherButtonEnabled()) {
                SelectedDate selectedDate = mDatePicker.getSelectedDate();
                switchButtonText = mListener.formatDate(selectedDate);

                if (TextUtils.isEmpty(switchButtonText)) {
                    if (selectedDate.getType() == SelectedDate.Type.SINGLE) {
                        Date toFormat = new Date(mDatePicker.getSelectedDateInMillis());
                        switchButtonText = mDefaultDateFormatter.format(toFormat);
                    } else if (selectedDate.getType() == SelectedDate.Type.RANGE) {
                        switchButtonText = formatDateRange(selectedDate);
                    }
                }

                mButtonLayout.updateSwitcherText(SublimeOptions.Picker.TIME_PICKER, switchButtonText);
            }
        }
    }

    private String formatDateRange(SelectedDate selectedDate) {
        Calendar startDate = selectedDate.getStartDate();
        Calendar endDate = selectedDate.getEndDate();

        startDate.set(Calendar.MILLISECOND, 0);
        startDate.set(Calendar.SECOND, 0);
        startDate.set(Calendar.MINUTE, 0);
        startDate.set(Calendar.HOUR, 0);

        endDate.set(Calendar.MILLISECOND, 0);
        endDate.set(Calendar.SECOND, 0);
        endDate.set(Calendar.MINUTE, 0);
        endDate.set(Calendar.HOUR, 0);
        // Move to next day since we are nulling out the time fields
        endDate.add(Calendar.DAY_OF_MONTH, 1);

        float elapsedTime = endDate.getTimeInMillis() - startDate.getTimeInMillis();

        if (elapsedTime >= DateUtils.YEAR_IN_MILLIS) {
            final float years = elapsedTime / DateUtils.YEAR_IN_MILLIS;

            boolean roundUp = years - (int) years > 0.5f;
            final int yearsVal = roundUp ? (int) (years + 1) : (int) years;

            return "~" + yearsVal + " " + (yearsVal == 1 ? "year" : "years");
        } else if (elapsedTime >= MONTH_IN_MILLIS) {
            final float months = elapsedTime / MONTH_IN_MILLIS;

            boolean roundUp = months - (int) months > 0.5f;
            final int monthsVal = roundUp ? (int) (months + 1) : (int) months;

            return "~" + monthsVal + " " + (monthsVal == 1 ? "month" : "months");
        } else {
            final float days = elapsedTime / DateUtils.DAY_IN_MILLIS;

            boolean roundUp = days - (int) days > 0.5f;
            final int daysVal = roundUp ? (int) (days + 1) : (int) days;

            return "~" + daysVal + " " + (daysVal == 1 ? "day" : "days");
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        return new SavedState(super.onSaveInstanceState(), mCurrentPicker, mHiddenPicker);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        BaseSavedState bss = (BaseSavedState) state;
        super.onRestoreInstanceState(bss.getSuperState());
        SavedState ss = (SavedState) bss;

        mCurrentPicker = ss.getCurrentPicker();

        mHiddenPicker = ss.getHiddenPicker();
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        super.dispatchRestoreInstanceState(container);
        updateDisplay();
    }

    /**
     * Class for managing state storing/restoring.
     */
    private static class SavedState extends View.BaseSavedState {

        private final SublimeOptions.Picker sCurrentPicker, sHiddenPicker /*One of DatePicker/TimePicker*/;

        /**
         * Constructor called from {@link SublimePicker#onSaveInstanceState()}
         */
        private SavedState(Parcelable superState, SublimeOptions.Picker currentPicker,
                           SublimeOptions.Picker hiddenPicker) {
            super(superState);

            sCurrentPicker = currentPicker;
            sHiddenPicker = hiddenPicker;
        }

        /**
         * Constructor called from {@link #CREATOR}
         */
        private SavedState(Parcel in) {
            super(in);

            sCurrentPicker = SublimeOptions.Picker.valueOf(in.readString());
            sHiddenPicker = SublimeOptions.Picker.valueOf(in.readString());
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            super.writeToParcel(dest, flags);

            dest.writeString(sCurrentPicker.name());
            dest.writeString(sHiddenPicker.name());
        }

        public SublimeOptions.Picker getCurrentPicker() {
            return sCurrentPicker;
        }

        public SublimeOptions.Picker getHiddenPicker() {
            return sHiddenPicker;
        }

        @SuppressWarnings("all")
        // suppress unused and hiding
        public static final Parcelable.Creator<SavedState> CREATOR = new Creator<SavedState>() {

            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    private void processOptions() {
        if (mOptions.animateLayoutChanges()) {
            // Basic Layout Change Animation(s)
            LayoutTransition layoutTransition = new LayoutTransition();
            if (SUtils.isApi_16_OrHigher()) {
                layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
            }
            setLayoutTransition(layoutTransition);
        } else {
            setLayoutTransition(null);
        }

        mDatePickerEnabled = mOptions.isDatePickerActive();
        mTimePickerEnabled = mOptions.isTimePickerActive();

        if (mDatePickerEnabled) {
            mDatePicker.init(mOptions.getDateParams(), mOptions.canPickDateRange(), this);

            long[] dateRange = mOptions.getDateRange();

            if (dateRange[0] /* min date */ != Long.MIN_VALUE) {
                mDatePicker.setMinDate(dateRange[0]);
            }

            if (dateRange[1] /* max date */ != Long.MIN_VALUE) {
                mDatePicker.setMaxDate(dateRange[1]);
            }

            mDatePicker.setValidationCallback(this);

        } else {
            llMainContentHolder.removeView(mDatePicker);
            mDatePicker = null;
        }

        if (mTimePickerEnabled) {
            int[] timeParams = mOptions.getTimeParams();
            mTimePicker.setCurrentHour(timeParams[0] /* hour of day */);
            mTimePicker.setCurrentMinute(timeParams[1] /* minute */);
            mTimePicker.setIs24HourView(mOptions.is24HourView());
            mTimePicker.setValidationCallback(this);

        } else {
            llMainContentHolder.removeView(mTimePicker);
            mTimePicker = null;
        }

        if (mDatePickerEnabled && mTimePickerEnabled) {
            mButtonLayout.applyOptions(true /* show switch button */,
                    mOptions.getNeutralButtonText(),
                    mButtonLayoutCallback);
        } else {
            mButtonLayout.applyOptions(false /* hide switch button */,
                    mOptions.getNeutralButtonText(),
                    mButtonLayoutCallback);
        }

        if (!mDatePickerEnabled && !mTimePickerEnabled) {
            removeView(llMainContentHolder);
            llMainContentHolder = null;
            mButtonLayout = null;
        }

        mCurrentPicker = mOptions.getPickerToShow();
        // Updated from 'updateDisplay()' when 'RecurrencePicker' is chosen
        mHiddenPicker = SublimeOptions.Picker.INVALID;
    }

    private void reassessValidity() {
        mButtonLayout.updateValidity(mDatePickerValid && mTimePickerValid);
    }

    @Override
    public void onDateChanged(SublimeDatePicker view, SelectedDate selectedDate) {
        mDatePicker.init(selectedDate, mOptions.canPickDateRange(), this);
    }

    @Override
    public void onDatePickerValidationChanged(boolean valid) {
        mDatePickerValid = valid;
        reassessValidity();
    }

    @Override
    public void onTimePickerValidationChanged(boolean valid) {
        mTimePickerValid = valid;
        reassessValidity();
    }
}
