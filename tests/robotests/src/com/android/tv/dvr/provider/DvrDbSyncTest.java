/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.tv.dvr.provider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.ProgramImpl;
import com.android.tv.data.api.Program;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.WritableDvrDataManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.dvr.recorder.SeriesRecordingScheduler;
import com.android.tv.testing.TestSingletonApp;
import com.android.tv.testing.constants.ConfigConstants;

import org.robolectric.RobolectricTestRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.util.concurrent.RoboExecutorService;
import org.robolectric.annotation.Config;

/** Tests for {@link com.android.tv.dvr.DvrScheduleManager} */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = ConfigConstants.SDK, application = TestSingletonApp.class)
public class DvrDbSyncTest {
    private static final String INPUT_ID = "input_id";
    private static final long BASE_PROGRAM_ID = 1;
    private static final long BASE_START_TIME_MS = 0;
    private static final long BASE_END_TIME_MS = 1;
    private static final String BASE_SEASON_NUMBER = "2";
    private static final String BASE_EPISODE_NUMBER = "3";
    private ProgramImpl baseProgram;
    private ProgramImpl baseSeriesProgram;
    private ScheduledRecording baseSchedule;
    private ScheduledRecording baseSeriesSchedule;

    private DvrDbSync mDbSync;
    @Mock private DvrManager mDvrManager;
    @Mock private WritableDvrDataManager mDataManager;
    @Mock private ChannelDataManager mChannelDataManager;
    @Mock private SeriesRecordingScheduler mSeriesRecordingScheduler;

    @Before
    public void setUp() {
        // TODO(b/69843199): make these static finals
        baseProgram =
                new ProgramImpl.Builder()
                        .setId(BASE_PROGRAM_ID)
                        .setStartTimeUtcMillis(BASE_START_TIME_MS)
                        .setEndTimeUtcMillis(BASE_END_TIME_MS)
                        .build();
        baseSeriesProgram =
                new ProgramImpl.Builder()
                        .setId(BASE_PROGRAM_ID)
                        .setStartTimeUtcMillis(BASE_START_TIME_MS)
                        .setEndTimeUtcMillis(BASE_END_TIME_MS)
                        .setSeasonNumber(BASE_SEASON_NUMBER)
                        .setEpisodeNumber(BASE_EPISODE_NUMBER)
                        .build();
        baseSchedule = ScheduledRecording.builder(INPUT_ID, baseProgram).build();
        baseSeriesSchedule = ScheduledRecording.builder(INPUT_ID, baseSeriesProgram).build();

        MockitoAnnotations.initMocks(this);
        when(mChannelDataManager.isDbLoadFinished()).thenReturn(true);
        when(mDvrManager.addSeriesRecording(any(), any(), anyInt()))
                .thenReturn(SeriesRecording.builder(INPUT_ID, baseProgram).build());
        mDbSync =
                new DvrDbSync(
                        RuntimeEnvironment.application.getApplicationContext(),
                        mDataManager,
                        mChannelDataManager,
                        mDvrManager,
                        mSeriesRecordingScheduler,
                        new RoboExecutorService());
    }

    @Test
    public void testHandleUpdateProgram_null() {
        addSchedule(BASE_PROGRAM_ID, baseSchedule);
        mDbSync.handleUpdateProgram(null, BASE_PROGRAM_ID);
        verify(mDataManager).removeScheduledRecording(baseSchedule);
    }

    @Test
    public void testHandleUpdateProgram_changeTimeNotStarted() {
        addSchedule(BASE_PROGRAM_ID, baseSchedule);
        long startTimeMs = BASE_START_TIME_MS + 1;
        long endTimeMs = BASE_END_TIME_MS + 1;
        Program program =
                new ProgramImpl.Builder(baseProgram)
                        .setStartTimeUtcMillis(startTimeMs)
                        .setEndTimeUtcMillis(endTimeMs)
                        .build();
        mDbSync.handleUpdateProgram(program, BASE_PROGRAM_ID);
        assertUpdateScheduleCalled(program);
    }

    @Test
    public void testHandleUpdateProgram_changeTimeInProgressNotCalled() {
        addSchedule(
                BASE_PROGRAM_ID,
                ScheduledRecording.buildFrom(baseSchedule)
                        .setState(ScheduledRecording.STATE_RECORDING_IN_PROGRESS)
                        .build());
        long startTimeMs = BASE_START_TIME_MS + 1;
        Program program =
                new ProgramImpl.Builder(baseProgram).setStartTimeUtcMillis(startTimeMs).build();
        mDbSync.handleUpdateProgram(program, BASE_PROGRAM_ID);
        verify(mDataManager, never()).updateScheduledRecording(any());
    }

    @Test
    public void testHandleUpdateProgram_changeSeason() {
        addSchedule(BASE_PROGRAM_ID, baseSeriesSchedule);
        String seasonNumber = BASE_SEASON_NUMBER + "1";
        String episodeNumber = BASE_EPISODE_NUMBER + "1";
        Program program =
                new ProgramImpl.Builder(baseSeriesProgram)
                        .setSeasonNumber(seasonNumber)
                        .setEpisodeNumber(episodeNumber)
                        .build();
        mDbSync.handleUpdateProgram(program, BASE_PROGRAM_ID);
        assertUpdateScheduleCalled(program);
    }

    @Test
    public void testHandleUpdateProgram_finished() {
        addSchedule(
                BASE_PROGRAM_ID,
                ScheduledRecording.buildFrom(baseSeriesSchedule)
                        .setState(ScheduledRecording.STATE_RECORDING_FINISHED)
                        .build());
        String seasonNumber = BASE_SEASON_NUMBER + "1";
        String episodeNumber = BASE_EPISODE_NUMBER + "1";
        Program program =
                new ProgramImpl.Builder(baseSeriesProgram)
                        .setSeasonNumber(seasonNumber)
                        .setEpisodeNumber(episodeNumber)
                        .build();
        mDbSync.handleUpdateProgram(program, BASE_PROGRAM_ID);
        verify(mDataManager, never()).updateScheduledRecording(any());
    }

    private void addSchedule(long programId, ScheduledRecording schedule) {
        when(mDataManager.getScheduledRecordingForProgramId(programId)).thenReturn(schedule);
    }

    private void assertUpdateScheduleCalled(Program program) {
        verify(mDataManager)
                .updateScheduledRecording(
                        eq(ScheduledRecording.builder(INPUT_ID, program).build()));
    }
}
