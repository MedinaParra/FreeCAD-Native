"""Runtime compatibility hooks loaded automatically by embedded CPython."""
import builtins
import math
builtins.math = math
