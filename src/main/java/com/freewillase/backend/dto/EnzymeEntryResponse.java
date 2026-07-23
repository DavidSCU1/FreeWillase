package com.freewillase.backend.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class EnzymeEntryResponse {
    Long id;
    String accession;
    String proteinName;
    String organismName;
    String taxId;
    Integer sequenceLength;
    String sequenceHash;
    String structureType;
    String structureId;
    String structureSourceDb;
    String structureUrl;
    String ncbiProteinAccession;
    String ncbiProteinUrl;
    String uniprotAccession;
    String uniprotUrl;
    String pdbId;
    String pdbUrl;
    LocalDateTime createdAt;
}
