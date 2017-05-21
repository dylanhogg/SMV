#
# This file is licensed under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""SMV PartialTime and TimePanel Framework

This module defines the most used PartialTime including `Day`, `Month`, `Quarter`, and the TimePanel
"""

from smvapp import SmvApp

def Quarter(year, quarter):
    """Define an smv.panel.Quarter

        Quarter extends smv.panel.PartialTime base class. It has the following methods,
            * smvTime(): returns (str), will be the value of the `smvTime` column if added to a DF
            * timeIndex(): returns (int), an integer incremental by one as the PartialTime increase
                by one unit
            * timeLabel(): returns (str), a human readable string
            * timeType(): returns (str), the type of the PartialTime

        Args:
            year (int):
            quarter (int):

        Example:

            >>> q = Quarter(2012, 1)
            >>> q.smvTime()
            u'Q201201'
            >>> q.timeIndex()
            168
            >>> q.timeLabel()
            u'2012-Q1'
            >>> q.timeType()
            u'quarter'

        Returns:
            (java object smv.panel.Quarter)
    """
    return SmvApp.getInstance()._jvm.Quarter(year, quarter)

def Month(year, month):
    """Define an smv.panel.Month

        Quarter extends smv.panel.PartialTime base class. It has the following methods,
            * smvTime(): returns (str), will be the value of the `smvTime` column if added to a DF
            * timeIndex(): returns (int), an integer incremental by one as the PartialTime increase
                by one unit
            * timeLabel(): returns (str), a human readable string
            * timeType(): returns (str), the type of the PartialTime

        Args:
            year (int):
            month (int):

        Example:

            >>> m = Month(2012, 5)
            >>> m.smvTime()
            u'M201205'
            >>> m.timeIndex()
            508
            >>> m.timeLabel()
            u'2012-05'
            >>> m.timeType()
            u'month'

        Returns:
            (java object smv.panel.Month)
    """
    return SmvApp.getInstance()._jvm.Month(year, month)

def Day(year, month, day):
    """Define an smv.panel.Day

        Quarter extends smv.panel.PartialTime base class. It has the following methods,
            * smvTime(): returns (str), will be the value of the `smvTime` column if added to a DF
            * timeIndex(): returns (int), an integer incremental by one as the PartialTime increase
                by one unit
            * timeLabel(): returns (str), a human readable string
            * timeType(): returns (str), the type of the PartialTime

        Args:
            year (int):
            month (int):
            day (int):

        Example:

            >>> d = Day(2012, 5, 31)
            >>> d.smvTime()
            u'D20120531'
            >>> d.timeIndex()
            15491
            >>> d.timeLabel()
            u'2012-05-31'
            >>> d.timeType()
            u'day'

        Returns:
            (java object smv.panel.Day)
    """
    return SmvApp.getInstance()._jvm.Day(year, month, day)

def Week(year, month, day, start_on = "Monday"):
    """Define an smv.panel.Week

        Quarter extends smv.panel.PartialTime base class. It has the following methods,
            * smvTime(): returns (str), will be the value of the `smvTime` column if added to a DF
            * timeIndex(): returns (int), an integer incremental by one as the PartialTime increase
                by one unit
            * timeLabel(): returns (str), a human readable string
            * timeType(): returns (str), the type of the PartialTime

        Args:
            year (int):
            month (int):
            day (int):
            start_on (str): Week starts on, valid values: Monday, Tuesday, Wednesday,
                Thursday, Friday, Saturday, Sunday. Default value is Monday

        Example:
            >>> w = Week(2012, 3, 4)
            >>> w.smvTime()
            u'W20120227'
            >>> w.timeIndex()
            2200
            >>> w.timeLabel()
            u'Week of 2012-02-27'
            >>> w.timeType()
            u'week'

            >>> w = Week(2012, 3, 4, "Sunday")
            >>> w.timeType()
            u'week_start_on_Sunday'
            >>> w.smvTime()
            u'W(7)20120304'
            >>> w.timeIndex()
            2201
            >>> w.timeLabel()
            u'Week of 2012-03-04'

        Returns:
            (java object smv.panel.Week)
    """
    return SmvApp.getInstance()._jvm.Week(year, month, day, start_on)

def TimePanel(start, end):
    """Define an smv.panel.TimePanel

        Args:
            start (java object smv.PartialTime): Quarter, Month, Day etc.
            end (java object smv.PartialTime): Quarter, Month, Day etc.

        Example:

            >>> tp = TimePanel(Day(2012, 1, 1), Day(2013, 12, 31))

        Returns:
            (java object smv.panel.TimePanel)
    """
    return SmvApp.getInstance()._jvm.TimePanel(start, end)
