/*
 * Copyright (c) 2022 Razeware LLC
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * Notwithstanding the foregoing, you may not use, copy, modify, merge, publish,
 * distribute, sublicense, create a derivative work, and/or sell copies of the
 * Software in any work that is designed, intended, or marketed for pedagogical or
 * instructional purposes related to programming, coding, application development,
 * or information technology.  Permission for such use, copying, modification,
 * merger, publication, distribution, sublicensing, creation of derivative works,
 * or sale is expressly withheld.
 * 
 * This project and source code may use libraries or frameworks that are
 * released under various Open-Source licenses. Use of those libraries and
 * frameworks are governed by their own individual licenses.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.yourcompany.android.awarenessfood

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.lifecycle.*
import com.google.android.material.snackbar.Snackbar
import com.yourcompany.android.awarenessfood.data.Recipe
import com.yourcompany.android.awarenessfood.databinding.ActivityMainBinding
import com.yourcompany.android.awarenessfood.monitor.NetworkMonitor
import com.yourcompany.android.awarenessfood.monitor.NetworkState
import com.yourcompany.android.awarenessfood.monitor.UnavailableConnectionLifecycleOwner
import com.yourcompany.android.awarenessfood.repositories.models.RecipeApiState
import com.yourcompany.android.awarenessfood.viewmodels.MainViewModel
import com.yourcompany.android.awarenessfood.viewmodels.UiLoadingState
import com.yourcompany.android.awarenessfood.views.IngredientView
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main Screen
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

  @Inject
  lateinit var unavailableConnectionLifecycleOwner: UnavailableConnectionLifecycleOwner

  private lateinit var networkMonitor: NetworkMonitor
  private val networkObserver = NetworkObserver()

  private val viewModel: MainViewModel by viewModels()
  private lateinit var binding: ActivityMainBinding
  private var snackbar: Snackbar? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    networkMonitor = NetworkMonitor(this)

    viewModel.recipeState.observe(this, Observer {
      when (it) {
        RecipeApiState.Error -> showNetworkUnavailableAlert(R.string.error)
        is RecipeApiState.Result -> buildViews(it.recipe)
      }
    })
    viewModel.getRandomRecipe()

    // 1. Network Monitor Initialization
    networkMonitor.init()

    networkMonitor.networkAvailableStateFlow.asLiveData().observe(this, Observer { networkState ->
      handleNetworkState(networkState)
    })
  }

  // 2. Register network callback.
  override fun onStart() {
    super.onStart()
    networkMonitor.registerNetworkCallback()
  }

  // 3. Unregister network callback.
  override fun onStop() {
    super.onStop()
    networkMonitor.unregisterNetworkCallback()
  }

  override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
    R.id.menu_refresh -> {
      clearViews()
      viewModel.getRandomRecipe()
      true
    }
    R.id.menu_trivia -> {
      startActivity(Intent(this, FoodTriviaActivity::class.java))
      true
    }
    else -> super.onOptionsItemSelected(item)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.main_menu, menu)
    return true
  }

  private fun buildViews(recipe: Recipe) {
    with(binding) {
      recipeInstructionsTitle.text = getString(R.string.instructions)
      recipeIngredientsTitle.text = getString(R.string.ingredients)
      recipeName.text = recipe.title
      recipeSummary.text = HtmlCompat.fromHtml(recipe.summary, 0)
      recipeInstructions.text = HtmlCompat.fromHtml(recipe.instructions, 0)
      Picasso.get().load(recipe.image).into(recipeImage)
      recipe.ingredients.forEachIndexed { index, ingredient ->
        val ingredientView = IngredientView(this@MainActivity)
        ingredientView.setIngredient(ingredient)
        if (index != 0) {
          ingredientView.addDivider()
        }
        recipeIngredients.addView(ingredientView)
      }
    }
  }

  private fun clearViews() {
    with(binding) {
      recipeName.text = ""
      recipeSummary.text = ""
      recipeInstructions.text = ""
      recipeImage.setImageDrawable(null)
      recipeIngredientsTitle.text = ""
      recipeIngredients.removeAllViews()
      recipeInstructionsTitle.text = ""
    }
  }

  private fun showNetworkUnavailableAlert(message: Int) {
    snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE)
        .setAction(R.string.retry) {
          viewModel.getRandomRecipe()
        }.apply {
          view.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.purple_500))
          show()
        }
  }

  private fun handleLoadingState(uiLoadingState: UiLoadingState?) {
    when (uiLoadingState) {
      UiLoadingState.Loading -> {
        clearViews()
        binding.progressBar.isVisible = true
      }
      UiLoadingState.NotLoading -> binding.progressBar.isVisible = false
      else -> {}
    }
  }

  private fun handleNetworkState(networkState: NetworkState?) {
    when (networkState) {
      NetworkState.Unavailable -> showNetworkUnavailableAlert(R.string.network_is_unavailable)
      else -> {}
    }
  }

  private fun removeNetworkUnavailableAlert() {
    snackbar?.dismiss()
  }

  inner class NetworkObserver : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
      onNetworkAvailable()
    }

    override fun onStop(owner: LifecycleOwner) {
      onNetworkUnavailable()
    }

    private fun onNetworkUnavailable() {
      showNetworkUnavailableAlert(R.string.network_is_unavailable)
    }

    private fun onNetworkAvailable() {
      removeNetworkUnavailableAlert()
    }
  }
}
