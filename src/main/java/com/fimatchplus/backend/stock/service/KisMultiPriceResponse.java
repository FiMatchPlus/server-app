package com.fimatchplus.backend.stock.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KisMultiPriceResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg_cd") String msgCd,
        @JsonProperty("msg1") String msg1,
        @JsonProperty("output") List<ResponseBodyOutput> output
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResponseBodyOutput(
            @JsonProperty("inter_shrn_iscd") String interShrnIscd,
            @JsonProperty("inter_kor_isnm") String interKorIsnm,
            @JsonProperty("inter2_prpr") String inter2Prpr,
            @JsonProperty("inter2_prdy_vrss") String inter2PrdyVrss,
            @JsonProperty("prdy_vrss_sign") String prdyVrssSign,
            @JsonProperty("prdy_ctrt") String prdyCtrt,
            @JsonProperty("inter2_prdy_clpr") String inter2PrdyClpr,
            @JsonProperty("inter2_oprc") String inter2Oprc,
            @JsonProperty("inter2_hgpr") String inter2Hgpr,
            @JsonProperty("inter2_lwpr") String inter2Lwpr,
            @JsonProperty("acml_vol") String acmlVol,
            @JsonProperty("acml_tr_pbmn") String acmlTrPbmn
    ) {}
}
