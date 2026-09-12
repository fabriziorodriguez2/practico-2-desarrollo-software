# Práctico 2 - Mitigación de vulnerabilidades CWE

**Estudiante:** Fabrizio Rodríguez  
**Rama de entrega:** `practico-2`

## Ejercicio 1 - SQL Injection

### Vulnerabilidad encontrada

Se identificó una vulnerabilidad de inyección SQL, correspondiente a **CWE-89: Improper Neutralization of Special Elements used in an SQL Command**.

La aplicación construía la consulta SQL concatenando directamente el texto ingresado por el usuario en el buscador:

```python
WHERE peliculas.nombre LIKE '%{query}%'
```
