package com.example.authdemo.dto;

import com.example.authdemo.model.Revision.RevisionResult;
import com.example.authdemo.model.Revision.RevisionFrequency;
import lombok.Data;
import java.time.LocalDate;

@Data
public class RevisionForm {
    private LocalDate revisionDate;
    private RevisionFrequency frequency;
    private RevisionResult result;
    private String description;
    private Long vehicleId;
}