package com.rynekryz.exiless.ui

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rynekryz.exiless.R
import com.rynekryz.exiless.data.Settings
import com.rynekryz.exiless.databinding.FragmentStatsBinding
import kotlinx.coroutines.launch

class StatsFragment : Fragment() {
    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()
    private lateinit var settings: Settings

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = Settings(requireContext())
        renderTotal()
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.items.collect { renderSession(it) }
            }
        }
    }

    private fun renderTotal() {
        val s = settings.stats
        binding.statsTotal.text = getString(
            R.string.stats_total_detail,
            s.files,
            Formatter.formatShortFileSize(requireContext(), s.bytes),
            s.gps,
        )
    }

    private fun renderSession(items: List<com.rynekryz.exiless.data.FileItem>) {
        val added = items.size
        val cleaned = items.count { it.cleaned != null }
        val removable = items.filter { it.cleaned == null }.sumOf { it.removable }
        val withGps = items.count { it.hasGps && it.cleaned == null }
        binding.statsSession.text = getString(
            R.string.stats_session_detail,
            added,
            cleaned,
            Formatter.formatShortFileSize(requireContext(), removable),
            withGps,
        )
    }

    override fun onResume() {
        super.onResume()
        renderTotal()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = StatsFragment()
    }
}
