package com.michalsvec.singlerowcalendar

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.michalsvec.singlerowcalendar.selection.CalendarDetailsLookup
import com.michalsvec.singlerowcalendar.selection.CalendarKeyProvider
import java.util.*

/**
 * @author Michal Švec
 * @since v1.0.0
 */

class SingleRowCalendar(context: Context, attrs: AttributeSet) : RecyclerView(context, attrs) {


    /**
     * we can disable long press to select when we select this key
     */
    private val GHOST_ITEM_KEY = -999999999999999299

    private lateinit var selectionTracker: SelectionTracker<Long>
    private var calendarChangesObserver: CalendarChangesObserver? = null
    val dateList: MutableList<Date> = mutableListOf()
    var previousMonthNumber = ""
    var previousYear = ""
    var multiSelection: Boolean
    var deselection: Boolean
    var longPress: Boolean
    var dateTextViewId: Int
    var dayTextViewId: Int
    var monthTextViewId: Int
    var pastDaysCount: Int
    var futureDaysCount: Int
    var includeCurrentDate: Boolean
    var initialPositionIndex: Int
    private var scrollPosition = 0
    lateinit var calendarViewType: CalendarViewManager


    init {
        itemAnimator = null // this remove blinking when clicking items

        context.theme.obtainStyledAttributes(attrs, R.styleable.SingleRowCalendar, 0, 0).apply {

            try {
                dateTextViewId = getResourceId(R.styleable.SingleRowCalendar_dateTextViewId, 0)
                dayTextViewId = getResourceId(R.styleable.SingleRowCalendar_dayTextViewId, 0)
                monthTextViewId = getResourceId(R.styleable.SingleRowCalendar_monthTextViewId, 0)
                pastDaysCount = getInt(R.styleable.SingleRowCalendar_pastDaysCount, 0)
                futureDaysCount = getInt(R.styleable.SingleRowCalendar_futureDaysCount, 30)
                includeCurrentDate =
                    getBoolean(R.styleable.SingleRowCalendar_includeCurrentDate, true)
                initialPositionIndex =
                    getInt(R.styleable.SingleRowCalendar_initialPositionIndex, pastDaysCount)
                multiSelection = getBoolean(R.styleable.SingleRowCalendar_multiSelection, false)
                deselection = getBoolean(R.styleable.SingleRowCalendar_deselection, true)
                longPress = getBoolean(R.styleable.SingleRowCalendar_longPress, false)
            } finally {
                recycle()
            }
        }
    }

    fun init() {

        this.apply {

            if (dateList.isNullOrEmpty()) {
                dateList.clear()
                dateList.addAll(loadDates(pastDaysCount, futureDaysCount, includeCurrentDate))
            }


            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            (this.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                initialPositionIndex,
                0
            )

            setHasFixedSize(true)

            val singleRowCalendarAdapter = SingleRowCalendarAdapter(dateList, calendarViewType)

            adapter = singleRowCalendarAdapter
            initSelection()

            SingleRowCalendarAdapter.selectionTracker = selectionTracker


            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {

                    calendarChangesObserver?.whenCalendarScrolled(dx, dy)

                    scrollPosition =
                        (layoutManager as LinearLayoutManager).findLastCompletelyVisibleItemPosition()

                    val lastVisibleItem = if (dx > 0)
                        (layoutManager as LinearLayoutManager).findLastCompletelyVisibleItemPosition()
                    else
                        (layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()

                    if (previousMonthNumber != DateUtils.getMonthNumber(dateList[lastVisibleItem]) || previousYear != DateUtils.getYear(dateList[lastVisibleItem]))

                        calendarChangesObserver?.whenMonthAndYearChanged(
                            DateUtils.getMonthNumber(dateList[lastVisibleItem]),
                            DateUtils.getMonthName(dateList[lastVisibleItem]),
                            DateUtils.getYear(dateList[lastVisibleItem]),
                            dateList[lastVisibleItem]
                        )

                    previousMonthNumber = DateUtils.getMonthNumber(dateList[lastVisibleItem])
                    previousYear = DateUtils.getYear(dateList[lastVisibleItem])
                }
            })
        }
    }


    private fun loadDates(pastDays: Int, futureDays: Int, includeCurrentDate: Boolean): List<Date> {
        val futureList = DateUtils.getFutureDates(futureDays)
        val cal = Calendar.getInstance(Locale.getDefault())
        val pastList = DateUtils.getPastDates(pastDays).reversed()
        return if (includeCurrentDate) pastList + cal.time + futureList else pastList + futureList
    }


    private fun initSelection() {
        selectionTracker = SelectionTracker.Builder(
            "singleRowCalendarSelectionTracker",
            this,
            CalendarKeyProvider(this),
            CalendarDetailsLookup(this),
            StorageStrategy.createLongStorage()
        ).withSelectionPredicate(object : SelectionTracker.SelectionPredicate<Long>() {
            override fun canSelectMultiple(): Boolean {
                return multiSelection
            }

            override fun canSetStateAtPosition(position: Int, nextState: Boolean): Boolean =
                if (deselection)
                    if (nextState)
                        deselection
                    else
                        false
                else
                    true


            override fun canSetStateForKey(key: Long, nextState: Boolean): Boolean =
                if (deselection)
                    if (nextState)
                        deselection
                    else
                        false
                else
                    true

        }).build()


        if (!longPress)
            disableLongPress()



        selectionTracker.addObserver(object : SelectionTracker.SelectionObserver<Long>() {
            override fun onItemStateChanged(key: Long, selected: Boolean) {

                // todo druha cat podmienky
                if (key != GHOST_ITEM_KEY && key.toInt() < dateList.size)
                    calendarChangesObserver?.whenSelectionChanged(
                        selected,
                        key.toInt(),
                        dateList[key.toInt()]
                    )

                if (selectionTracker.selection.size() == 0)
                    disableLongPress()

                super.onItemStateChanged(key, selected)

            }

            override fun onSelectionRefresh() {
                calendarChangesObserver?.whenSelectionRefreshed()
                super.onSelectionRefresh()
            }

            override fun onSelectionRestored() {
                calendarChangesObserver?.whenSelectionRestored()
                super.onSelectionRestored()
            }

        })


    }


    fun setCalendarChangesObserver(CalendarChangesObserver: CalendarChangesObserver) {
        this.calendarChangesObserver = CalendarChangesObserver
    }

    private fun disableLongPress() =
        selectionTracker.select(GHOST_ITEM_KEY)


    fun clearSelection() {
        selectionTracker.clearSelection()
        if (!longPress)
            disableLongPress()
    }

    //WORKS
    fun select(position: Int) = selectionTracker.select(position.toLong())

    //WORKS
    fun setItemsSelected(positionList: List<Int>, selected: Boolean) {
        val longList = positionList.map { it.toLong() }
        selectionTracker.setItemsSelected(longList, selected)
    }

    //WORKS
    fun deselect(position: Int) = selectionTracker.deselect(position.toLong())

    //WORKS
    fun isSelected(position: Int) = selectionTracker.isSelected(position.toLong())

    //WORKS //TODO CHECK GHOST ITEM
    fun hasSelection(): Boolean = getSelectedIndexes().isNotEmpty()

    //WORKS
    fun onRestoreInstanceState(state: Bundle) = selectionTracker.onRestoreInstanceState(state)

    //WORKS
    fun onSaveInstanceState(state: Bundle) = selectionTracker.onSaveInstanceState(state)

    //WORKS
    fun getSelectedDates(): List<Date> {
        val selectionList: MutableList<Date> = mutableListOf()
        selectionTracker.selection.forEach {
            if (it != GHOST_ITEM_KEY && it.toInt() < dateList.size)
                selectionList.add(dateList[it.toInt()])
        }
        return selectionList
    }

    //WORKS
    fun getSelectedIndexes(): List<Int> {
        val selectionList: MutableList<Int> = mutableListOf()
        selectionTracker.selection.forEach {
            if (it != GHOST_ITEM_KEY && it.toInt() < dateList.size)
                selectionList.add(it.toInt())
        }
        return selectionList
    }


    fun changeDates(newDateList: List<Date>) {
        val diffCallback = DateDiffCallback(dateList, newDateList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        dateList.clear()
        dateList.addAll(newDateList)
        if (adapter != null)
            diffResult.dispatchUpdatesTo(adapter!!)
        if (scrollPosition > dateList.size - 1)
            scrollPosition = dateList.size - 1
        scrollToPosition(scrollPosition)
        calendarChangesObserver?.whenMonthAndYearChanged(
            DateUtils.getMonthNumber(dateList[scrollPosition]),
            DateUtils.getMonthName(dateList[scrollPosition]),
            DateUtils.getYear(dateList[scrollPosition]),
            dateList[scrollPosition]
        )
    }
}