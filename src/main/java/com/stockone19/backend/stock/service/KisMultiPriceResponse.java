package com.stockone19.backend.stock.service;

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
            @JsonProperty("inter_shrn_iscd") String interShrnIscd,        // 종목코드
            @JsonProperty("inter_kor_isnm") String interKorIsnm,          // 종목 한글명
            @JsonProperty("inter2_prpr") String inter2Prpr,               // 현재가
            @JsonProperty("inter2_prdy_vrss") String inter2PrdyVrss,      // 전일대비
            @JsonProperty("prdy_vrss_sign") String prdyVrssSign,          // 전일대비부호
            @JsonProperty("prdy_ctrt") String prdyCtrt,                   // 전일대비율
            @JsonProperty("inter2_prdy_clpr") String inter2PrdyClpr,      // 전일종가
            @JsonProperty("inter2_oprc") String inter2Oprc,               // 시가
            @JsonProperty("inter2_hgpr") String inter2Hgpr,               // 고가
            @JsonProperty("inter2_lwpr") String inter2Lwpr,               // 저가
            @JsonProperty("acml_vol") String acmlVol,                     // 누적거래량
            @JsonProperty("acml_tr_pbmn") String acmlTrPbmn               // 누적거래대금
    ) {}
}
