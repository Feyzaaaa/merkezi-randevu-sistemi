package com.hastane.merkezi_randevu_sistemi.dto;
import lombok.Data;

@Data
public class ClinicalNoteRequest {
    private String note;

    // IDE'nin Lombok hatasını aşmak için köprüleri manuel ekledik:
    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}