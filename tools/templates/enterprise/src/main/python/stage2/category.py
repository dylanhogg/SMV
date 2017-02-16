from smv import *
from pyspark.sql.functions import col, sum, lit

from _PROJ_CLASS_.stage2 import inputdata

__all__ = ['PythonEmploymentByStateCategory']

class PythonEmploymentByStateCategory(SmvPyModule, SmvPyOutput):
    """Python ETL Example: employment by state with category"""

    def requiresDS(self):
        return [inputdata.EmploymentByStateLink]

    def run(self, i):
        df = i[inputdata.EmploymentByStateLink]
        return df.smvSelectPlus((col("EMP") > lit(1000000)).alias("cat_high_emp"))
