package dev.meridianwealth.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meridianwealth.testsupport.StaticPage;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Catálogo no app (item #33): o seletor, a busca rápida e a abertura sob demanda, sem nenhum preço inventado quando não há dado real. */
class CatalogContractTest {

    @Test
    void thePickerSearchesTheFullCatalogAfterAPauseAndNeverLetsAnOldAnswerOverwriteTheNewOne() throws IOException {
        String picker = StaticPage.file("picker.js");
        assertThat(picker).contains("function mountAssetPicker(host, items, onPick, remote)").contains("setTimeout(async () => {").contains("}, 220);")
                .contains("if (mine !== seq || list.hidden) return;").contains("t.length < 2").contains(" · catálogo");
        assertThat(picker).as("o catálogo completa o que já há, sem repetir símbolo").contains("have.has(u.symbol)");
    }

    @Test
    void openingAnAssetTracksItOnTheServerAndExplainsWhenThereIsNoRealData() throws IOException {
        String js = StaticPage.file("app.js");
        assertThat(js).contains("async function catalogSearch(q)").contains("/api/catalog/search?limit=20&q=").contains("async function openAsset(sym,force)")
                .contains("'/api/catalog/'+encodeURIComponent(sym)+'/track',{method:'POST'}");
        assertThat(js).contains("r.status===503").contains("Nada foi inventado.").contains("r.status===429");
        assertThat(js).as("os seletores e a busca rápida abrem o ativo antes de selecioná-lo").contains("openAsset(sym).then(ok=>{if(ok)select(sym)}),catalogSearch")
                .contains("openAsset(sym).then(ok=>{if(ok){select(sym);go('cockpit')}})");
    }

    @Test
    void theQuickSearchAddsTheCatalogResultsAtTheEndAndTheHintExplainsEveryListedAssetOpens() throws IOException {
        String js = StaticPage.file("app.js"), html = StaticPage.file("index.html");
        assertThat(js).contains("function palRemote(q)").contains("CATÁLOGO COMPLETO").contains("if(mine!==palSeq||!$('#pal').open)return;");
        assertThat(html).contains("Qualquer ativo negociado na B3 ou nos EUA").contains("ações, FIIs, ETFs e BDRs");
        assertThat(StaticPage.file("cosmos.css")).contains(".cs-toast{position:fixed");
    }

    @Test
    void ifTheServerForgotAnOnDemandAssetTheSelectionReopensItOnceInsteadOfFailingSilently() throws IOException {
        // medido: depois de reiniciar o servidor, openAsset devolvia true sem chamar e o select lançava 404; agora reabre com force e tenta de novo uma vez
        String js = StaticPage.file("app.js");
        assertThat(js).contains("async function openAsset(sym,force)").contains("if(!force&&rows[sym]&&universe.some(u=>u.symbol===sym))return true;")
                .contains("if(await openAsset(sym,true)){try{got=await load()}catch(e2){got=null}}").contains("if(got){candles=got[0];A=got[1]}else{candles=[];A=null}");
    }
}
