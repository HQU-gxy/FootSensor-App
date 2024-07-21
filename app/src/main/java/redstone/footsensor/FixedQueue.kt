package redstone.footsensor

import kotlin.collections.ArrayList

class FixedQueue<T>(private val length: Int) {

    private var _data = ArrayList<T>(length)

    private var _head = 0

    fun pushBack(element: T) {
        if (_data.size < length)
            _data.add(element)
        else
            _data[_head] = element

        if (++_head == length)
            _head = 0
    }


    fun getData(): ArrayList<T> {
        if (_head == 0)
            return _data

        if (_head == _data.size)
            return _data

        val firstDataSlice = _data.subList(_head, _data.size)
        val lastDataSlice = _data.subList(0, _head)
        return (firstDataSlice + lastDataSlice) as ArrayList
    }
}