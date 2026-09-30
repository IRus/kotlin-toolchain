/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import org.jetbrains.amper.events.sink.EventSink
import org.jetbrains.amper.junit.event.JUnitEventProtocol
import org.jetbrains.amper.tasks.jvm.StructuredJUnitProcessOutputListener
import org.jetbrains.amper.testevents.TestDescriptor
import org.jetbrains.amper.testevents.TestEvent
import org.jetbrains.amper.testevents.TestFinished
import org.jetbrains.amper.testevents.TestId
import org.jetbrains.amper.testevents.TestReportEvent
import org.jetbrains.amper.testevents.TestSkipped
import org.jetbrains.amper.testevents.TestStderrEvent
import org.jetbrains.amper.testevents.TestStdoutEvent
import org.jetbrains.amper.testevents.TestSuiteAborted
import org.jetbrains.amper.testevents.TestSuiteSkipped
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class StructuredJUnitProcessOutputListenerTest {
    @Test
    fun `renders protocol output as test output events`() {
        val renderer = RecordingRenderer()
        val listener = StructuredJUnitProcessOutputListener(eventSink = renderer)

        listener.onStdoutLine(JUnitEventProtocol.encode(JUnitEventProtocol.Event.TestStdout("test", "protocol stdout")), pid = 1)
        listener.onStderrLine(JUnitEventProtocol.encode(JUnitEventProtocol.Event.TestStderr("test", "protocol stderr")), pid = 1)
        listener.onStdoutLine(JUnitEventProtocol.encode(JUnitEventProtocol.Event.TestStdout(null, "unattributed stdout")), pid = 1)
        listener.onStdoutLine("regular stdout", pid = 1)
        listener.onStderrLine("regular stderr", pid = 1)

        assertEquals(
            [
                TestStdoutEvent(TestId(renderer.runId, "test"), "protocol stdout"),
                TestStderrEvent(TestId(renderer.runId, "test"), "protocol stderr"),
                TestStdoutEvent(null, "unattributed stdout"),
                TestStdoutEvent(null, "regular stdout${System.lineSeparator()}"),
                TestStderrEvent(null, "regular stderr${System.lineSeparator()}"),
            ],
            renderer.events,
        )
    }

    @Test
    fun `preserves a report media type`() {
        val renderer = RecordingRenderer()
        val listener = StructuredJUnitProcessOutputListener(eventSink = renderer)

        listener.onStdoutLine(
            JUnitEventProtocol.encode(
                JUnitEventProtocol.Event.Report(
                    id = "test",
                    key = "attachment",
                    value = "/tmp/result.png",
                    mediaType = "image/png",
                    timestampMillis = 1_234L,
                )
            ),
            pid = 1,
        )

        val expected = [
            TestReportEvent(
                testId = TestId(renderer.runId, "test"),
                key = "attachment",
                value = "/tmp/result.png",
                mediaType = "image/png",
                timestamp = Instant.fromEpochMilliseconds(1_234L),
            )
        ]

        assertEquals(expected, renderer.events)
    }

    @Test
    @Disabled("Kotlin CLI needs to bootstrap to launch this test correctly. Otherwise, the old version of amper-junit-event-protocol classes added on the runtime take precedence.")
    fun `converts aborted and skipped protocol events`() {
        val renderer = RecordingRenderer()
        val listener = StructuredJUnitProcessOutputListener(eventSink = renderer)

        [
            JUnitEventProtocol.Event.SuiteAborted("suite", 10, "suite aborted"),
            JUnitEventProtocol.Event.SuiteSkipped(
                "skippedSuite",
                null,
                "Skipped suite",
                null,
                reason = "suite skipped"
            ),
            JUnitEventProtocol.Event.TestAborted("test", 20, "test aborted"),
            JUnitEventProtocol.Event.TestSkipped("test", "suite", "Skipped test", null, reason = "test skipped"),
        ].forEach { listener.onStdoutLine(JUnitEventProtocol.encode(it), pid = 1) }

        val descriptor = TestDescriptor(TestId(renderer.runId, "test"), TestId(renderer.runId, "suite"), "Skipped test")
        val suiteDescriptor = TestDescriptor(TestId(renderer.runId, "suite"), null, "Skipped suite")
        assertEquals(
            [
                TestSuiteAborted(TestId(renderer.runId, "suite"), 10.milliseconds, "suite aborted"),
                TestSuiteSkipped(suiteDescriptor.copy(id = TestId(renderer.runId, "skippedSuite")), "suite skipped"),
                TestFinished.Aborted(TestId(renderer.runId, "test"), 20.milliseconds, "test aborted"),
                TestSkipped(descriptor, "test skipped"),
            ],
            renderer.events,
        )
    }

    private class RecordingRenderer : EventSink<TestEvent> {
        val events: List<TestEvent>
         field = mutableListOf()

        val runId get() = events.firstNotNullOf { it.testId }.runId

        override fun emit(event: TestEvent) {
            events += event
        }
    }
}
