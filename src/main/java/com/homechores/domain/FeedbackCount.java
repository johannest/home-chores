package com.homechores.domain;

/** How many times one chore drew one reaction — a row of the home's all-time feedback
 *  tally, grouped in the database (see {@code CompletionRepository.feedbackByTask}). */
public record FeedbackCount(Long taskId, Feedback feedback, Long count) {
}
