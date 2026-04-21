package online.yanzacademy.fastbuy.dto;

public class ValidationResponse {
    private String status;

    public ValidationResponse(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
