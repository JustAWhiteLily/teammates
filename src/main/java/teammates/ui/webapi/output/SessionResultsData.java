package teammates.ui.webapi.output;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import teammates.common.datatransfer.FeedbackParticipantType;
import teammates.common.datatransfer.FeedbackSessionResultsBundle;
import teammates.common.datatransfer.attributes.FeedbackQuestionAttributes;
import teammates.common.datatransfer.attributes.FeedbackResponseAttributes;
import teammates.common.datatransfer.attributes.InstructorAttributes;
import teammates.common.datatransfer.attributes.StudentAttributes;
import teammates.common.datatransfer.questions.FeedbackQuestionDetails;
import teammates.common.datatransfer.questions.FeedbackQuestionType;
import teammates.common.datatransfer.questions.FeedbackResponseDetails;
import teammates.common.util.Const;

/**
 * API output format for session results, including statistics.
 */
public class SessionResultsData extends ApiOutput {

    private static final String REGEX_ANONYMOUS_PARTICIPANT_HASH = "[0-9]{1,10}";

    private final List<QuestionOutput> questions = new ArrayList<>();

    public SessionResultsData(FeedbackSessionResultsBundle bundle, InstructorAttributes instructor) {
        Map<FeedbackQuestionAttributes, List<FeedbackResponseAttributes>> questionsWithResponses =
                bundle.getQuestionResponseMapSortedByRecipient();

        questionsWithResponses.forEach((question, responses) -> {
            FeedbackQuestionDetails questionDetails = question.getQuestionDetails();
            QuestionOutput qnOutput = new QuestionOutput(question.questionNumber, question.getQuestionType(),
                    questionDetails.getQuestionText(),
                    questionDetails.getQuestionResultStatisticsJson(responses, question, instructor.email, bundle, false));

            List<ResponseOutput> allResponses = buildResponses(responses, bundle);
            for (ResponseOutput respOutput : allResponses) {
                qnOutput.allResponses.add(respOutput);
            }

            questions.add(qnOutput);
        });
    }

    public SessionResultsData(FeedbackSessionResultsBundle bundle, StudentAttributes student) {
        Map<FeedbackQuestionAttributes, List<FeedbackResponseAttributes>> questionsWithResponses =
                bundle.getQuestionResponseMapSortedByRecipient();

        questionsWithResponses.forEach((question, responses) -> {
            FeedbackQuestionDetails questionDetails = question.getQuestionDetails();
            QuestionOutput qnOutput = new QuestionOutput(question.questionNumber, question.getQuestionType(),
                    questionDetails.getQuestionText(),
                    questionDetails.getQuestionResultStatisticsJson(responses, question, student.email, bundle, true));

            if (questionDetails.isIndividualResponsesShownToStudents()) {
                List<ResponseOutput> allResponses = buildResponses(question, responses, bundle, student);
                for (ResponseOutput respOutput : allResponses) {
                    if ("You".equals(respOutput.giver)) {
                        qnOutput.responsesFromSelf.add(respOutput);
                    } else if ("You".equals(respOutput.recipient)) {
                        qnOutput.responsesToSelf.add(respOutput);
                    } else {
                        qnOutput.otherResponses.add(respOutput);
                    }
                }
            }

            questions.add(qnOutput);
        });
    }

    public List<QuestionOutput> getQuestions() {
        return questions;
    }

    private static String removeAnonymousHash(String identifier) {
        return identifier.replaceAll(Const.DISPLAYED_NAME_FOR_ANONYMOUS_PARTICIPANT + " (student|instructor|team) "
                + REGEX_ANONYMOUS_PARTICIPANT_HASH, Const.DISPLAYED_NAME_FOR_ANONYMOUS_PARTICIPANT + " $1");
    }

    private List<ResponseOutput> buildResponses(
            FeedbackQuestionAttributes question, List<FeedbackResponseAttributes> responses,
            FeedbackSessionResultsBundle bundle, StudentAttributes student) {
        Map<String, List<FeedbackResponseAttributes>> responsesMap = new HashMap<>();

        for (FeedbackResponseAttributes response : responses) {
            responsesMap.computeIfAbsent(response.recipient, k -> new ArrayList<>()).add(response);
        }

        List<ResponseOutput> output = new ArrayList<>();

        responsesMap.forEach((recipient, responsesForRecipient) -> {
            boolean isUserRecipient = student.email.equals(recipient);
            boolean isUserTeamRecipient = question.recipientType == FeedbackParticipantType.TEAMS
                    && student.team.equals(recipient);
            String recipientName;
            if (isUserRecipient) {
                recipientName = "You";
            } else if (isUserTeamRecipient) {
                recipientName = String.format("Your Team (%s)", bundle.getNameForEmail(recipient));
            } else {
                recipientName = bundle.getNameForEmail(recipient);
            }
            recipientName = removeAnonymousHash(recipientName);

            for (FeedbackResponseAttributes response : responsesForRecipient) {
                String giverName = bundle.getGiverNameForResponse(response);
                String displayedGiverName;

                boolean isUserGiver = student.email.equals(response.giver);
                boolean isUserPartOfGiverTeam = student.team.equals(giverName);
                if (question.giverType == FeedbackParticipantType.TEAMS && isUserPartOfGiverTeam) {
                    displayedGiverName = "Your Team (" + giverName + ")";
                } else if (isUserGiver) {
                    displayedGiverName = "You";
                } else {
                    displayedGiverName = removeAnonymousHash(giverName);
                }

                if (isUserGiver && !isUserRecipient) {
                    // If the giver is the user, show the real name of the recipient
                    // since the giver would know which recipient he/she gave the response to
                    recipientName = bundle.getNameForEmail(response.recipient);
                }

                // TODO fetch feedback response comments

                output.add(new ResponseOutput(recipientName, response.recipientSection,
                        displayedGiverName, response.giverSection, response.responseDetails));
            }

        });
        return output;
    }

    private List<ResponseOutput> buildResponses(
            List<FeedbackResponseAttributes> responses, FeedbackSessionResultsBundle bundle) {
        Map<String, List<FeedbackResponseAttributes>> responsesMap = new HashMap<>();

        for (FeedbackResponseAttributes response : responses) {
            responsesMap.computeIfAbsent(response.recipient, k -> new ArrayList<>()).add(response);
        }

        List<ResponseOutput> output = new ArrayList<>();

        responsesMap.forEach((recipient, responsesForRecipient) -> {
            String recipientName = removeAnonymousHash(bundle.getNameForEmail(recipient));

            for (FeedbackResponseAttributes response : responsesForRecipient) {
                String giverName = removeAnonymousHash(bundle.getGiverNameForResponse(response));

                // TODO fetch feedback response comments

                output.add(new ResponseOutput(recipientName, response.recipientSection,
                        giverName, response.giverSection, response.responseDetails));
            }

        });
        return output;
    }

    private static class QuestionOutput {

        private final String questionText;
        private final FeedbackQuestionType questionType;
        private final int questionNumber;
        private final String questionStatistics;

        // For instructor view
        private List<ResponseOutput> allResponses = new ArrayList<>();

        // For student view
        private List<ResponseOutput> responsesToSelf = new ArrayList<>();
        private List<ResponseOutput> responsesFromSelf = new ArrayList<>();
        private List<ResponseOutput> otherResponses = new ArrayList<>();

        QuestionOutput(int questionNumber, FeedbackQuestionType questionType, String questionText,
                String questionStatistics) {
            this.questionNumber = questionNumber;
            this.questionType = questionType;
            this.questionText = questionText;
            this.questionStatistics = questionStatistics;
        }

        public String getQuestionText() {
            return questionText;
        }

        public FeedbackQuestionType getQuestionType() {
            return questionType;
        }

        public int getQuestionNumber() {
            return questionNumber;
        }

        public String getQuestionStatistics() {
            return questionStatistics;
        }

        public List<ResponseOutput> getAllResponses() {
            return allResponses;
        }

        public List<ResponseOutput> getResponsesFromSelf() {
            return responsesFromSelf;
        }

        public List<ResponseOutput> getResponsesToSelf() {
            return responsesToSelf;
        }

        public List<ResponseOutput> getOtherResponses() {
            return otherResponses;
        }

    }

    private static class ResponseOutput {

        private final String giver;
        private final String giverSection;
        private final String recipient;
        private final String recipientSection;
        private final String responseMetadata;

        ResponseOutput(String giver, String giverSection, String recipient,
                String recipientSection, FeedbackResponseDetails responseDetails) {
            this.giver = giver;
            this.giverSection = giverSection;
            this.recipient = recipient;
            this.recipientSection = recipientSection;
            this.responseMetadata = responseDetails.getJsonString();
        }

        public String getGiver() {
            return giver;
        }

        public String getGiverSection() {
            return giverSection;
        }

        public String getRecipient() {
            return recipient;
        }

        public String getRecipientSection() {
            return recipientSection;
        }

        public String getResponseMetadata() {
            return responseMetadata;
        }
    }

}
