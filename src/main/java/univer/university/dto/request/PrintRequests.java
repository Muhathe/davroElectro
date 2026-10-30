package univer.university.dto.request;

public final class PrintRequests {
    private PrintRequests() {
    }

    public record AgentFailure(String error) {
    }
}
