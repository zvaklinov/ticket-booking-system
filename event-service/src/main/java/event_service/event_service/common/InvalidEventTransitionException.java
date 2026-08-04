package event_service.event_service.common;

public class InvalidEventTransitionException extends RuntimeException {
    public InvalidEventTransitionException(String message){
        super(message);
    }
}
