import requests


URL = "http://localhost:8080"


def test_listado_normal_funciona():
    respuesta = requests.get(URL, timeout=10)
    assert respuesta.status_code == 200
    assert '<span>Inception</span>' in respuesta.text


def test_ssti_no_evalua_expresiones():
    respuesta = requests.get(URL, params={"buscar": "7*7"}, timeout=10)
    assert respuesta.status_code == 200
    assert 'value="7*7"' in respuesta.text
    assert "Resultados buscando por: 49" not in respuesta.text
    assert "No se encontraron coincidencias." in respuesta.text


def test_rce_no_ejecuta_un_proceso():
    # Solo ejecutaría printf, sin archivos ni conexiones externas.
    payload = "T(java.lang.Runtime).getRuntime().exec('printf DSS_RCE_CONTROLADA')"
    respuesta = requests.get(URL, params={"buscar": payload}, timeout=10)
    assert respuesta.status_code == 200
    assert "Resultados buscando por: Process[pid=" not in respuesta.text
    assert "No se encontraron coincidencias." in respuesta.text
