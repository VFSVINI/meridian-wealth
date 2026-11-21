"""Testes da curadoria dos relatórios, com recortes reais da CVM: python -m unittest scripts/curadoria/test_gerar_relatorios.py"""
import sys
import tempfile
import unittest
import zipfile
from datetime import date
from decimal import Decimal
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gerar_relatorios as g  # noqa: E402

FIXTURES = Path(__file__).resolve().parents[2] / "src/test/resources/fixtures/cvm"
PETR = "33.000.167/0001-01"
ITAU = "60.872.504/0001-23"


class RelatoriosReais(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.zip = Path(cls.tmp.name) / "itr_cia_aberta_2026.zip"
        with zipfile.ZipFile(cls.zip, "w") as z:
            z.write(FIXTURES / "itr-2026-indice-recorte.csv", "itr_cia_aberta_2026.csv")
            z.write(FIXTURES / "itr-2026-dre-con-recorte.csv", "itr_cia_aberta_DRE_con_2026.csv")

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def relatorios(self, cnpj):
        return {r["rotulo"]: r for r in g.relatorios_da_empresa(cnpj, [("ITR", 2026, self.zip)])}

    def test_petrobras_3t2026_compara_com_o_mesmo_trimestre_do_ano_anterior(self):
        r = self.relatorios(PETR)["3T2026"]
        m = {x["codigo"]: x for x in r["metricas"]}
        self.assertEqual((m["RECEITA"]["atual"], m["RECEITA"]["base"]), ("169353000000", "127906000000"))
        self.assertEqual((m["RESULTADO_LIQUIDO"]["atual"], m["RESULTADO_LIQUIDO"]["base"]), ("39783000000", "32847000000"))
        self.assertEqual(m["RECEITA"]["periodoAtual"], "2026-07-01 a 2026-09-30")
        self.assertEqual(m["RECEITA"]["periodoBase"], "2025-07-01 a 2025-09-30")
        self.assertTrue(r["link"].startswith("https://"))

    def test_reentrega_usa_a_ultima_versao_para_os_numeros_e_a_primeira_para_o_prazo(self):
        r = self.relatorios(PETR)["1T2026"]
        self.assertEqual(r["versao"], 2)
        self.assertEqual(r["primeiraEntregaEm"], "2026-05-11")

    def test_menor_versao_na_base_revela_quando_a_entrega_original_falta(self):
        r = self.relatorios(PETR)
        self.assertEqual(r["1T2026"]["menorVersaoNaBase"], 1)
        # mesma base sem a linha da versão 1 do 1T2026: a entrega original deixa de constar
        sem_v1 = Path(self.tmp.name) / "sem_v1.zip"
        linhas = (FIXTURES / "itr-2026-indice-recorte.csv").read_bytes().split(b"\n")
        filtradas = [l for l in linhas if not (b";2026-03-31;1;PETROLEO" in l)]
        with zipfile.ZipFile(sem_v1, "w") as z:
            z.writestr("itr_cia_aberta_2026.csv", b"\n".join(filtradas))
            z.write(FIXTURES / "itr-2026-dre-con-recorte.csv", "itr_cia_aberta_DRE_con_2026.csv")
        out = {x["rotulo"]: x for x in g.relatorios_da_empresa(PETR, [("ITR", 2026, sem_v1)])}
        self.assertEqual(out["1T2026"]["menorVersaoNaBase"], 2)

    def test_banco_usa_a_conta_de_resultado_com_codigo_diferente(self):
        r = self.relatorios(ITAU)["3T2026"]
        m = {x["codigo"]: x for x in r["metricas"]}
        self.assertEqual(m["RESULTADO_LIQUIDO"]["contaNaFonte"], "3.09")
        self.assertEqual(m["RESULTADO_LIQUIDO"]["atual"], "12270000000")
        self.assertEqual(m["RECEITA"]["rotuloNaFonte"], "Receitas da Intermediação Financeira")

    def test_so_o_trimestre_isolado_nao_o_acumulado(self):
        m = {x["codigo"]: x for x in self.relatorios(PETR)["3T2026"]["metricas"]}
        self.assertNotEqual(m["RECEITA"]["atual"], "462569000000")  # acumulado de 9 meses


class Funcoes(unittest.TestCase):
    def test_exato_sem_zeros_a_direita_nem_notacao_cientifica(self):
        self.assertEqual(g.exato(Decimal("169353000000.0000000000")), "169353000000")
        self.assertEqual(g.exato(Decimal("0.006722")), "0.006722")
        self.assertEqual(g.exato(Decimal("-2515000000")), "-2515000000")

    def test_fim_do_mes_do_informe_mensal(self):
        self.assertEqual(g.fim_do_mes("2026-10-01"), "2026-10-31")
        self.assertEqual(g.fim_do_mes("2026-12-01"), "2026-12-31")
        self.assertEqual(g.fim_do_mes("2028-02-01"), "2028-02-29")

    def test_um_ano_antes_em_29_de_fevereiro(self):
        self.assertEqual(g.um_ano_antes(date(2028, 2, 29)), date(2027, 2, 28))

    def test_ordem_do_exercicio_sem_acento(self):
        self.assertEqual(g.ordem({"ORDEM_EXERC": "ÚLTIMO"}), "ULTIMO")
        self.assertEqual(g.ordem({"ORDEM_EXERC": "PENÚLTIMO"}), "PENULTIMO")


if __name__ == "__main__":
    unittest.main()
