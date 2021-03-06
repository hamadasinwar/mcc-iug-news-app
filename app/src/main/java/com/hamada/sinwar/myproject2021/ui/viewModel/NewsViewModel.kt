package com.hamada.sinwar.myproject2021.ui.viewModel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hamada.sinwar.myproject2021.app.NewsApplication
import com.hamada.sinwar.myproject2021.models.Article
import com.hamada.sinwar.myproject2021.models.NewsResponse
import com.hamada.sinwar.myproject2021.repository.NewsRepository
import com.hamada.sinwar.myproject2021.util.Resource
import kotlinx.coroutines.launch
import okio.IOException
import retrofit2.Response

class NewsViewModel(val app: NewsApplication, private val newsRepository: NewsRepository) : AndroidViewModel(app) {

    var breakingNewsPage = 1
    private var breakingNewsResponse: NewsResponse? = null

    init {
        getBreakingNews()
    }

    fun getBreakingNews() = viewModelScope.launch {
        safeBreakingNewsCall()
    }

    private fun handleBreakingNewsResponse(response: Response<NewsResponse>) : Resource<NewsResponse> {
        if(response.isSuccessful) {
            response.body()?.let { resultResponse ->
                breakingNewsPage++
                if (breakingNewsResponse == null){
                    breakingNewsResponse = resultResponse
                }else{
                    val oldArticles = breakingNewsResponse?.articles
                    val newArticles = resultResponse.articles
                    oldArticles?.addAll(newArticles)
                }
                return Resource.Success(breakingNewsResponse?:resultResponse)
            }
        }
        return Resource.Error(response.message())
    }

    fun saveArticle(article: Article) = viewModelScope.launch {
        newsRepository.replace(article)
    }

    fun getSavedNews() = newsRepository.getSavedNews()

    fun deleteArticle(article: Article) = viewModelScope.launch {
        newsRepository.deleteArticle(article)
    }

    private suspend fun safeBreakingNewsCall(){
        app.breakingNews.postValue((Resource.Loading()))
        try {
            if (hasInternetConnection()) {
                val response = newsRepository.getBreakingNews(breakingNewsPage)
                app.breakingNews.postValue(handleBreakingNewsResponse(response))
            }else{
                app.breakingNews.postValue(Resource.Error("No internet connection"))
            }
        }catch (t: Throwable){
            when(t){
                is IOException -> app.breakingNews.postValue(Resource.Error("Network Failure"))
                else -> app.breakingNews.postValue(Resource.Error("Conversion Error"))
            }
        }
    }

    private fun hasInternetConnection():Boolean{
        val connectionManager = getApplication<NewsApplication>().getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager
        val activeNetwork = connectionManager.activeNetwork?: return false
        val capabilities = connectionManager.getNetworkCapabilities(activeNetwork)?:return false
        return when{
            capabilities.hasTransport(TRANSPORT_WIFI) -> true
            capabilities.hasTransport(TRANSPORT_CELLULAR) -> true
            capabilities.hasTransport(TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }
}