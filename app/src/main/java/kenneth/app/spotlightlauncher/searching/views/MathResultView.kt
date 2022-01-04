package kenneth.app.spotlightlauncher.searching.views

import android.content.Context
import android.view.LayoutInflater
import android.widget.LinearLayout
import kenneth.app.spotlightlauncher.R
import kenneth.app.spotlightlauncher.databinding.MathResultLayoutBinding
//import kenneth.app.spotlightlauncher.searching.SearchResult
//import kenneth.app.spotlightlauncher.searching.SmartSearcher

/**
 * Displays result of equation user entered in the search box.
 */
class MathResultView(context: Context) : LinearLayout(context) {
    private val binding: MathResultLayoutBinding

    init {
        val sectionCardPadding = resources.getDimensionPixelSize(R.dimen.card_padding)

        orientation = VERTICAL
        layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).also {
            it.setMargins(sectionCardPadding, 0, sectionCardPadding, sectionCardPadding)
        }

        binding = MathResultLayoutBinding.inflate(LayoutInflater.from(context), this)
    }
//
//    /**
//     * Shows the given math result
//     */
//    fun showResult(result: SearchResult.Suggested.Math) {
//        binding.equationText.text = result.query
//        binding.equationResultText.text = result.result.toString()
//    }
}